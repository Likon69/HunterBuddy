package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.resource.GatePolicy;
import com.hunterbuddy.behavior.resource.Lease;
import com.hunterbuddy.behavior.resource.LeaseState;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.util.QuietClicks;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.events.packets.ContainerSlotUpdateEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.SetCursorItemS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPlayerInventoryS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public final class InventoryBridge {
    private final WorldReader world;
    private final LongSupplier ticks;
    private final Consumer<String> alert;
    private ScreenHandler handler;
    private Screen screen;
    private BlockPos opened;
    private net.minecraft.block.Block requestedBlock;
    private final ItemStack[] playerReceipts = new ItemStack[41];
    private long playerVersion;
    private volatile WorldIdentity identity;
    private volatile long epoch;
    private BlockPos requested;
    private long requestedAt;
    private Ticket ticket;
    private Emission emission = Emission.NONE;
    private String quarantine;
    private long suspendedAt = -1;
    private final Deque<CursorReceipt> cursors = new ArrayDeque<>();
    private Lease quietLease;
    private long quietAt = -1;

    public boolean readyForClick(Lease lease) {
        require(lease, Resource.INVENTORY);
        if (!world.available()) return false;
        if (quietLease == lease && quietAt == ticks.getAsLong()) return true;
        if (!com.hunterbuddy.util.QuietClicks.readyOrSettle()) return false;
        quietLease = lease;
        quietAt = ticks.getAsLong();
        return true;
    }

    public InventoryBridge(WorldReader world, LongSupplier ticks, Consumer<String> alert) {
        this.world = Objects.requireNonNull(world);
        this.ticks = Objects.requireNonNull(ticks);
        this.alert = Objects.requireNonNull(alert);
    }

    public boolean open(BlockPos pos, Lease lease) {
        require(lease, Resource.SCREEN);
        require(lease, Resource.INTERACT);
        require(lease, Resource.LOOK);
        if (isOurs() && container()) return pos.equals(opened);
        if (isOurs() && !transactionPending()) forgetScreen();
        if (quarantine != null || !world.available() || world.client().currentScreen != null || !world.loaded(pos)) return false;
        if (requested != null) return false;
        net.minecraft.block.Block block = world.state(pos).getBlock();
        if (!(block instanceof net.minecraft.block.ChestBlock) && !(block instanceof net.minecraft.block.ShulkerBoxBlock)
            && block != net.minecraft.block.Blocks.ENDER_CHEST) return false;
        if (!(world.client().crosshairTarget instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)
            || world.client().player.getEyePos().squaredDistanceTo(hit.getPos()) > 4.5 * 4.5) return false;
        identity = world.identity();
        requested = pos.toImmutable();
        requestedBlock = block;
        requestedAt = ticks.getAsLong();
        emission = Emission.OWN;
        try {
            world.client().interactionManager.interactBlock(world.client().player, Hand.MAIN_HAND, hit);
        } finally {
            emission = Emission.NONE;
        }
        return true;
    }

    public boolean isOurs() {
        return world.available() && identity != null && identity.equals(world.identity()) && handler != null
            && world.client().player.currentScreenHandler == handler && world.client().currentScreen == screen;
    }

    public boolean ready() {
        return isOurs() && (handler.syncId > 0 || handler == world.client().player.playerScreenHandler);
    }

    public InventoryTransaction firstTransfer() {
        if (!ready() || !container()) {
            throw new IllegalStateException("owned chest is not synchronized");
        }
        for (Slot from : handler.slots) {
            if (from.inventory == world.client().player.getInventory() || from.getStack().isEmpty() || !from.canTakeItems(world.client().player)) continue;
            for (Slot to : handler.slots) {
                if (to.inventory != world.client().player.getInventory() || to.getIndex() < 0 || to.getIndex() > 8 || !to.getStack().isEmpty()) continue;
                return new InventoryTransaction(handler.syncId, from.id, to.id, item(from.getStack()), from.getStack().getCount(), SlotActionType.SWAP);
            }
        }
        throw new IllegalStateException("chest needs an item and an empty player hotbar slot");
    }

    public boolean hasMatchingItem(Predicate<ItemStack> filter) {
        if (!ready()) return false;
        for (Slot slot : handler.slots) {
            if (slot.inventory == world.client().player.getInventory() && !slot.getStack().isEmpty() && filter.test(slot.getStack())) return true;
        }
        return false;
    }

    public InventoryTransaction firstDeposit(Predicate<ItemStack> filter) {
        return firstDeposit(filter, FailureCode.NO_OPTION);
    }

    public InventoryTransaction firstDeposit(Predicate<ItemStack> filter, FailureCode fullCode) {
        if (!ready() || !container()) {
            throw new IllegalStateException("owned chest is not synchronized");
        }
        for (Slot from : handler.slots) {
            if (from.inventory != world.client().player.getInventory() || from.getStack().isEmpty()
                || !filter.test(from.getStack()) || !from.canTakeItems(world.client().player)) continue;
            if (!depositAllowed(from)) throw new IllegalStateException("deposit would enter the reserve of 25 obsidian");
            for (Slot to : handler.slots) {
                if (to.inventory == world.client().player.getInventory() || !to.canInsert(from.getStack()) || capacity(to, from.getStack()) == 0) continue;
                return new InventoryTransaction(handler.syncId, from.id, to.id, item(from.getStack()), from.getStack().getCount(), SlotActionType.QUICK_MOVE);
            }
        }
        throw new OperationFailure(fullCode, "chest has no free space for a deposit");
    }

    public Ticket click(InventoryTransaction transaction, Lease lease, long nowMs, Consumer<String> confirmed) {
        require(lease, Resource.INVENTORY);
        require(lease, Resource.SCREEN);
        if (!readyForClick(lease)) throw new IllegalStateException("inventory clicks need settling");
        if (!ready() || quarantine != null || !handler.getCursorStack().isEmpty()) throw new IllegalStateException("inventory authority or empty cursor required");
        if (ticket != null && !ticket.finished()) throw new IllegalStateException("inventory transaction already pending");
        if (handler.syncId != transaction.syncId() || transaction.fromSlot() >= handler.slots.size() || transaction.toSlot() >= handler.slots.size()) {
            throw new IllegalStateException("inventory handler changed");
        }
        Slot from = handler.slots.get(transaction.fromSlot());
        Slot to = handler.slots.get(transaction.toSlot());
        if (!matches(from.getStack(), transaction.expectedItemId(), transaction.expectedCount()) || !from.canTakeItems(world.client().player)) {
            throw new IllegalStateException("source item changed");
        }
        if (transaction.action() == SlotActionType.SWAP && (to.inventory != world.client().player.getInventory()
            || to.getIndex() < 0 || to.getIndex() > 8 || !to.canInsert(from.getStack()) || !from.canInsert(to.getStack())
            || from.getStack().getCount() > to.getMaxItemCount(from.getStack()) || to.getStack().getCount() > from.getMaxItemCount(to.getStack()))) {
            throw new IllegalStateException("SWAP requires a hotbar destination");
        }
        if (transaction.transferCount() > 0 && (!container()
            || from.inventory == world.client().player.getInventory() || to.inventory != world.client().player.getInventory()
            || !to.canInsert(from.getStack()) || capacity(to, from.getStack()) < transaction.transferCount())) {
            throw new IllegalStateException("bounded pickup requires a container source and enough player stack capacity");
        }
        if (transaction.action() == SlotActionType.QUICK_MOVE && !container()) {
            throw new IllegalStateException("QUICK_MOVE requires a container");
        }
        if (transaction.action() == SlotActionType.QUICK_MOVE && !depositAllowed(from)) throw new IllegalStateException("deposit would enter the reserve of 25 obsidian");
        epoch++;
        ticket = new Ticket(transaction, lease, nowMs, handler.getRevision(), Objects.requireNonNull(confirmed));
        ticket.before = handler.slots.stream().map(slot -> slot.getStack().copy()).toList();
        ticket.received = new java.util.HashMap<>();
        emit(transaction.fromSlot(), transaction.action() == SlotActionType.SWAP ? to.getIndex() : 0, transaction.action());
        return ticket;
    }

    private void emit(int slot, int button, SlotActionType action) {
        emission = Emission.OWN;
        try {
            world.client().getNetworkHandler().sendPacket(new ClickSlotC2SPacket(handler.syncId, handler.getRevision(),
                (short) slot, (byte) button, action, new Int2ObjectOpenHashMap<>(),
                ItemStackHash.fromItemStack(handler.getCursorStack(), world.client().getNetworkHandler().getComponentHasher())));
        } finally {
            emission = Emission.NONE;
        }
    }

    public boolean screenOpen() {
        return world.available() && world.client().currentScreen != null;
    }

    public boolean close() {
        if (!isOurs()) return false;
        if (quarantine != null || !handler.getCursorStack().isEmpty() || ticket != null && !ticket.finished()) return false;
        if (handler != world.client().player.playerScreenHandler) world.client().player.closeHandledScreen();
        forgetScreen();
        return true;
    }

    public boolean release(Lease lease) {
        if (ticket == null || ticket.lease != lease || ticket.stage == Stage.QUARANTINED || ticket.stage == Stage.RELEASED) return true;
        if (ticket.stage != Stage.RETURNING) {
            ticket.stage = Stage.RETURNING;
            ticket.revokeAt = ticks.getAsLong();
        }
        return restoreCursor();
    }

    private boolean restoreCursor() {
        if (ticket.transaction.transferCount() > 0) {
            if (ticket.serverConfirmed) { ticket.stage = Stage.RELEASED; return true; }
            if (!isOurs()) { quarantine("bounded pickup lost its inventory authority"); return true; }
            return false;
        }
        if (ticket.transaction.action() != SlotActionType.PICKUP && ticket.serverConfirmed) { ticket.stage = Stage.RELEASED; return true; }
        if (!isOurs()) {
            quarantine("inventory handler or player authority changed");
            return true;
        }
        if (ticket.transaction.action() != SlotActionType.PICKUP && handler.getCursorStack().isEmpty()) {
            if (!ticket.serverConfirmed) return false;
            ticket.stage = Stage.RELEASED;
            return true;
        }
        if (ticket.cursor == null && !ticket.emittedReturn) return false;
        if (ticket.cursor != null && ticket.cursor.count == 0) {
            ticket.stage = Stage.RELEASED;
            return true;
        }
        if (!ticket.emittedReturn && ticket.cursor != null) {
            Slot from = handler.slots.get(ticket.transaction.fromSlot());
            if (handler.getCursorStack().isEmpty()) return false;
            if (!matches(handler.getCursorStack(), ticket.transaction.expectedItemId(), ticket.transaction.expectedCount())
                || !ItemStack.areEqual(handler.getCursorStack(), ticket.before.get(ticket.transaction.fromSlot()))
                || !from.getStack().isEmpty() || !from.canInsert(handler.getCursorStack())) {
                quarantine("cursor cannot be returned safely");
                return true;
            }
            if (!com.hunterbuddy.util.QuietClicks.readyOrSettle()) return false;
            ticket.emittedReturn = true;
            ticket.cursor = null;
            emit(from.id, 0, SlotActionType.PICKUP);
        }
        return false;
    }

    public void suspend() {
        if (suspendedAt < 0) suspendedAt = ticks.getAsLong();
        if (ticket != null && !ticket.finished() && ticket.transaction.action() == SlotActionType.PICKUP) release(ticket.lease);
    }

    public void resume() {
        if (requested != null && suspendedAt >= 0) requestedAt += ticks.getAsLong() - suspendedAt;
        suspendedAt = -1;
    }

    public void stop() {
        suspendedAt = -1;
        requested = null;
        if (ticket != null && !ticket.finished()) release(ticket.lease);
        else if (isOurs() && !handler.getCursorStack().isEmpty()) quarantine("cursor belongs to another actor at stop");
        else if (isOurs()) close();
    }

    public void advance() {
        if (requested != null && (!world.available() || !identity.equals(world.identity()) || suspendedAt < 0 && ticks.getAsLong() - requestedAt >= 100)) requested = null;
        synchronized (cursors) {
            while (!cursors.isEmpty()) {
                CursorReceipt receipt = cursors.removeFirst();
                if (ticket != null && receipt.epoch == epoch && receipt.identity.equals(identity) && world.available() && world.client().getNetworkHandler() != null
                    && receipt.connection == world.client().getNetworkHandler().getConnection()) acceptCursor(receipt.stack);
            }
        }
        advanceBoundedPickup();
        if (ticket != null && ticket.stage == Stage.RETURNING) {
            if (!restoreCursor() && ticks.getAsLong() - ticket.revokeAt >= GatePolicy.LEGACY.inventoryGraceTicks()) quarantine("cursor return not confirmed within 20 ticks");
        }
        if (quarantine != null && world.available() && world.client().player.currentScreenHandler.getCursorStack().isEmpty()) {
            quarantine = null;
            ticket = null;
            forgetScreen();
        }
    }

    public void worldLost() {
        quietLease = null;
        quietAt = -1;
        java.util.Arrays.fill(playerReceipts, null);
        if (ticket != null && !ticket.finished()) quarantine("session ended during an inventory transaction");
        forgetScreen();
        synchronized (cursors) { cursors.clear(); }
    }

    public String alertStatus() {
        return quarantine != null || ticket != null && ticket.stage == Stage.RETURNING ? status() : null;
    }

    public String status() {
        if (quarantine != null) return "INVENTORY QUARANTINED: " + quarantine;
        return ticket == null ? "INVENTORY idle" : "INVENTORY " + ticket.stage + " syncId=" + ticket.transaction.syncId();
    }

    private void quarantine(String cause) {
        if (ticket != null) ticket.stage = Stage.QUARANTINED;
        if (quarantine == null) alert.accept("Inventory quarantined: " + cause + "; finish the cursor manually");
        quarantine = cause;
        requested = null;
    }

    private void forgetScreen() {
        handler = null;
        opened = null;
        screen = null;
        requested = null;
    }

    private void claim() {
        if (requested == null || !world.available() || world.legacyRegear() || !identity.equals(world.identity())) return;
        if (!world.loaded(requested) || world.state(requested).getBlock() != requestedBlock) return;
        ScreenHandler candidate = world.client().player.currentScreenHandler;
        if ((requestedBlock instanceof net.minecraft.block.ShulkerBoxBlock) != (candidate instanceof ShulkerBoxScreenHandler)) return;
        if ((candidate instanceof GenericContainerScreenHandler || candidate instanceof ShulkerBoxScreenHandler) && candidate.syncId > 0 && world.client().currentScreen instanceof HandledScreen<?>) {
            handler = candidate;
            screen = world.client().currentScreen;
            opened = requested;
            requested = null;
        }
    }

    @EventHandler
    private void inventory(InventoryEvent event) {
        claim();
        observeContents(event.packet.syncId(), event.packet.contents());
        if (!receiptAuthority() || event.packet.syncId() != ticket.transaction.syncId() || event.packet.revision() <= ticket.revision) return;
        for (int slot = 0; slot < event.packet.contents().size(); slot++) acceptSlot(slot, event.packet.contents().get(slot));
        acceptCursor(StackValue.of(event.packet.cursorStack()));
    }

    @EventHandler
    private void slot(ContainerSlotUpdateEvent event) {
        observeSlot(event.packet.getSyncId(), event.packet.getSlot(), event.packet.getStack());
        if (receiptAuthority() && event.packet.getSyncId() == ticket.transaction.syncId() && event.packet.getRevision() > ticket.revision) {
            acceptSlot(event.packet.getSlot(), event.packet.getStack());
        }
    }

    private boolean receiptAuthority() {
        return ticket != null && isOurs();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void receive(PacketEvent.Receive event) {
        if (event.isCancelled() || !world.available() || event.connection != world.client().getNetworkHandler().getConnection()) return;
        if (event.packet instanceof SetPlayerInventoryS2CPacket update) {
            WorldIdentity capturedWorld = world.identity();
            long capturedEpoch = epoch;
            ItemStack capturedStack = update.contents().copy();
            world.client().execute(() -> {
                if (world.available() && capturedWorld.equals(world.identity())) {
                    observePlayer(update.slot(), capturedStack);
                    if (receiptAuthority() && epoch == capturedEpoch && handler.syncId == 0) {
                        for (Slot slot : handler.slots) if (slot.inventory == world.client().player.getInventory() && slot.getIndex() == update.slot()) acceptSlot(slot.id, capturedStack);
                    }
                }
            });
        }
        if (event.packet instanceof net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket equipment
            && equipment.getEntityId() == world.client().player.getId()) {
            WorldIdentity equipmentWorld = world.identity();
            for (var pair : equipment.getEquipmentList()) if (pair.getFirst() == net.minecraft.entity.EquipmentSlot.CHEST) {
                ItemStack chestStack = pair.getSecond().copy();
                world.client().execute(() -> {
                    if (world.available() && equipmentWorld.equals(world.identity())) observePlayer(38, chestStack);
                });
            }
        }
        if (!(event.packet instanceof SetCursorItemS2CPacket packet)) return;
        WorldIdentity captured = identity;
        if (captured == null) return;
        synchronized (cursors) {
            if (cursors.size() >= 64) cursors.removeFirst();
            cursors.addLast(new CursorReceipt(captured, epoch, event.connection, StackValue.of(packet.contents())));
        }
    }

    @EventHandler
    private void send(PacketEvent.Send event) {
        if (requested != null && emission != Emission.OWN && event.packet instanceof PlayerInteractBlockC2SPacket) requested = null;
        if (event.packet instanceof ClickSlotC2SPacket click && emission != Emission.OWN) {
            if (ticket != null && click.syncId() == ticket.transaction.syncId() && !ticket.finished()
                && (!ticket.serverConfirmed || ticket.transaction.action() == SlotActionType.PICKUP)) quarantine("another actor clicked the inventory");
            if (handler != null && click.syncId() == handler.syncId) forgetScreen();
        }
    }

    public void retryOpen() {
        if (ticket != null && !ticket.finished()) throw new IllegalStateException("cannot replay a pending transaction");
        forgetScreen();
    }

    public boolean transactionPending() {
        return ticket != null && !ticket.finished();
    }

    private void acceptSlot(int slot, ItemStack stack) {
        if (ticket == null || ticket.stage != Stage.SENT && ticket.stage != Stage.RETURNING) return;
        ticket.received.put(slot, stack.copy());
        confirm();
    }

    private void acceptCursor(StackValue cursor) {
        if (ticket == null) return;
        ticket.cursor = cursor;
        confirm();
    }

    private void confirm() {
        if (ticket.serverConfirmed || ticket.stage != Stage.SENT && ticket.stage != Stage.RETURNING) return;
        InventoryTransaction tx = ticket.transaction;
        if (tx.transferCount() > 0) {
            confirmBoundedPickup();
            return;
        }
        ItemStack source = ticket.received.get(tx.fromSlot());
        if (source == null) return;
        ItemStack before = ticket.before.get(tx.fromSlot());
        boolean received = false;
        if (tx.action() == SlotActionType.SWAP) {
            ItemStack destination = ticket.received.get(tx.toSlot());
            received = destination != null && ItemStack.areEqual(destination, before)
                && ItemStack.areEqual(source, ticket.before.get(tx.toSlot()));
        } else if (tx.action() == SlotActionType.QUICK_MOVE) {
            int remaining = source.isEmpty() ? 0 : ItemStack.areItemsAndComponentsEqual(source, before) ? source.getCount() : before.getCount();
            int moved = before.getCount() - remaining;
            int gained = 0;
            boolean fromPlayer = handler.slots.get(tx.fromSlot()).inventory == world.client().player.getInventory();
            for (Slot slot : handler.slots) {
                if ((slot.inventory == world.client().player.getInventory()) == fromPlayer) continue;
                ItemStack next = ticket.received.get(slot.id);
                if (next == null) continue;
                ItemStack previous = ticket.before.get(slot.id);
                gained += matchingCount(next, before) - matchingCount(previous, before);
            }
            received = moved > 0 && gained == moved;
        } else {
            received = source.isEmpty() && ticket.cursor != null && ticket.cursor.id.equals(tx.expectedItemId())
                && ticket.cursor.count == tx.expectedCount();
        }
        if (received) {
            if (ticket.stage == Stage.SENT) ticket.stage = Stage.CONFIRMED;
            ticket.serverConfirmed = true;
            ticket.confirmed.accept("TRANSACTION_CONFIRMED syncId=" + tx.syncId() + " from=" + tx.fromSlot() + " to=" + tx.toSlot());
        }
    }

    private void confirmBoundedPickup() {
        InventoryTransaction tx = ticket.transaction;
        ItemStack before = ticket.before.get(tx.fromSlot());
        ItemStack source = ticket.received.get(tx.fromSlot());
        ItemStack destination = ticket.received.get(tx.toSlot());
        int destinationBefore = matchingCount(ticket.before.get(tx.toSlot()), before);
        if (ticket.partialReturning) {
            if (source == null || destination == null || ticket.cursor == null || ticket.cursor.count != 0
                || matchingCount(source, before) != before.getCount() - ticket.partialMoved
                || matchingCount(destination, before) != destinationBefore + ticket.partialMoved) return;
            if (ticket.stage == Stage.SENT) ticket.stage = Stage.CONFIRMED;
            ticket.serverConfirmed = true;
            ticket.confirmed.accept("BOUNDED_TRANSACTION_CONFIRMED syncId=" + tx.syncId() + " moved=" + ticket.partialMoved);
            return;
        }
        int moved = ticket.partialStarted ? ticket.partialMoved + 1 : 0;
        if (ticket.cursor == null || !ticket.cursor.id.equals(tx.expectedItemId())
            || ticket.cursor.count != before.getCount() - moved) return;
        if (!ticket.partialStarted && (source == null || !source.isEmpty())) return;
        if (ticket.partialStarted && (destination == null
            || matchingCount(destination, before) != destinationBefore + moved)) return;
        ticket.partialMoved = moved;
        ticket.partialReady = true;
    }

    private void advanceBoundedPickup() {
        if (ticket == null || ticket.transaction.transferCount() == 0 || !ticket.partialReady
            || ticket.serverConfirmed || !isOurs()
            || ticket.stage != Stage.SENT && ticket.stage != Stage.RETURNING) return;
        if (ticket.stage == Stage.RETURNING ? !QuietClicks.readyOrSettle() : !readyForClick(ticket.lease)) return;
        InventoryTransaction tx = ticket.transaction;
        boolean returning = ticket.stage == Stage.RETURNING || ticket.partialMoved >= tx.transferCount();
        ticket.partialReady = false;
        ticket.partialStarted = true;
        ticket.partialReturning = returning;
        ItemStack destination = ticket.received.get(tx.toSlot());
        ticket.received.clear();
        if (returning) ticket.received.put(tx.toSlot(), destination == null ? ticket.before.get(tx.toSlot()) : destination);
        ticket.cursor = null;
        emit(returning ? tx.fromSlot() : tx.toSlot(), returning ? 0 : 1, SlotActionType.PICKUP);
    }

    private static int matchingCount(ItemStack stack, ItemStack expected) {
        return !stack.isEmpty() && ItemStack.areItemsAndComponentsEqual(stack, expected) ? stack.getCount() : 0;
    }

    private static int capacity(Slot slot, ItemStack stack) {
        ItemStack present = slot.getStack();
        return present.isEmpty() ? slot.getMaxItemCount(stack)
            : ItemStack.areItemsAndComponentsEqual(present, stack) ? Math.max(0, slot.getMaxItemCount(stack) - present.getCount()) : 0;
    }

    private boolean depositAllowed(Slot from) {
        if (from.inventory != world.client().player.getInventory() || !from.getStack().isOf(net.minecraft.item.Items.OBSIDIAN)) return true;
        int capacity = 0;
        for (Slot to : handler.slots) if (to.inventory != from.inventory && to.canInsert(from.getStack())) capacity += capacity(to, from.getStack());
        return Math.min(from.getStack().getCount(), capacity) <= Math.max(0, world.count(net.minecraft.item.Items.OBSIDIAN) - 25);
    }

    public boolean container() {
        return ready() && (handler instanceof GenericContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler);
    }

    public boolean at(BlockPos pos) { return container() && pos.equals(opened); }

    public boolean enderChest() {
        return container() && opened != null && world.loaded(opened) && world.state(opened).isOf(net.minecraft.block.Blocks.ENDER_CHEST);
    }

    public List<ItemStack> containerContents() {
        if (!container()) throw new IllegalStateException("owned container required");
        return handler.slots.stream().filter(slot -> slot.inventory != world.client().player.getInventory()).map(slot -> slot.getStack().copy()).toList();
    }

    public boolean claimPlayer(Lease lease) {
        require(lease, Resource.INVENTORY);
        if (!world.available() || quarantine != null || world.client().currentScreen != null
            || world.client().player.currentScreenHandler != world.client().player.playerScreenHandler) return false;
        handler = world.client().player.playerScreenHandler;
        screen = null;
        identity = world.identity();
        opened = null;
        return true;
    }

    public InventoryTransaction playerSwap(int fromIndex, int hotbar) {
        if (!ready() || handler != world.client().player.playerScreenHandler) throw new IllegalStateException("owned player inventory required");
        Slot from = playerSlot(fromIndex);
        Slot to = playerSlot(hotbar);
        return new InventoryTransaction(0, from.id, to.id, item(from.getStack()), from.getStack().getCount(), SlotActionType.SWAP);
    }

    private Slot playerSlot(int index) {
        return handler.slots.stream().filter(slot -> slot.inventory == world.client().player.getInventory() && slot.getIndex() == index).findFirst().orElseThrow();
    }

    public InventoryTransaction takeStack(int containerSlot, ItemStack expected) {
        if (!container() || containerSlot < 0 || containerSlot >= containerContents().size()) throw new IllegalStateException("container slot missing");
        Slot from = handler.slots.get(containerSlot);
        if (!ItemStack.areEqual(from.getStack(), expected)) throw new IllegalStateException("precise stack changed");
        Slot free = handler.slots.stream().filter(slot -> slot.inventory == world.client().player.getInventory()
            && slot.getIndex() < 36 && slot.getStack().isEmpty()).findFirst().orElseThrow(() -> new IllegalStateException("free player slot required"));
        return new InventoryTransaction(handler.syncId, from.id, free.id, item(expected), expected.getCount(), SlotActionType.QUICK_MOVE);
    }

    public boolean mergesWithoutNewSlot(ItemStack expected, int maximum) {
        if (!container()) return false;
        int count = Math.min(expected.getCount(), maximum);
        if (count < 1) return false;
        int total = 0;
        for (Slot slot : handler.slots) {
            if (slot.inventory != world.client().player.getInventory() || slot.getIndex() >= 36
                || slot.getStack().isEmpty() || !slot.canInsert(expected)) continue;
            int available = capacity(slot, expected);
            if (count < expected.getCount() && available >= count) return true;
            total += available;
        }
        return count == expected.getCount() && total >= count;
    }

    public InventoryTransaction takeStackUpTo(int containerSlot, ItemStack expected, int maximum) {
        if (maximum < 1) throw new IllegalArgumentException("positive stock transfer required");
        if (expected.getCount() <= maximum) return takeStack(containerSlot, expected);
        if (!container() || containerSlot < 0 || containerSlot >= containerContents().size()) {
            throw new IllegalStateException("stock container slot missing");
        }
        Slot from = handler.slots.get(containerSlot);
        if (!ItemStack.areEqual(from.getStack(), expected)) throw new IllegalStateException("stock source changed");
        Slot destination = handler.slots.stream().filter(slot -> slot.inventory == world.client().player.getInventory()
            && slot.getIndex() < 36 && slot.canInsert(expected) && capacity(slot, expected) >= maximum)
            .min(Comparator.comparingInt(slot -> slot.getStack().isEmpty() ? 1 : 0))
            .orElseThrow(() -> new OperationFailure(FailureCode.NO_OPTION, "player has no space for shelter stock"));
        return new InventoryTransaction(handler.syncId, from.id, destination.id, item(expected), expected.getCount(),
            SlotActionType.PICKUP, maximum);
    }

    public InventoryTransaction takeStackToHotbar(int containerSlot, ItemStack expected, int hotbar) {
        if (!container() || hotbar < 0 || hotbar > 8) throw new IllegalStateException("owned container and hotbar required");
        if (containerSlot < 0 || containerSlot >= containerContents().size()) throw new IllegalStateException("source slot missing");
        Slot from = handler.slots.get(containerSlot);
        Slot destination = playerSlot(hotbar);
        if (!ItemStack.areEqual(from.getStack(), expected) || !destination.getStack().isEmpty()) {
            throw new IllegalStateException("precise source changed or destination occupied");
        }
        return new InventoryTransaction(handler.syncId, from.id, destination.id, item(expected), expected.getCount(), SlotActionType.SWAP);
    }

    public InventoryTransaction takeShulker(ShulkerSignature signature) {
        if (!enderChest()) throw new IllegalStateException("owned ender chest required");
        List<ItemStack> contents = containerContents();
        for (int i = 0; i < contents.size(); i++) if (signature.matches(contents.get(i))) return takeStack(i, contents.get(i));
        throw new IllegalStateException("expected shulker absent from ender chest");
    }

    public InventoryTransaction storeShulker(ShulkerSignature signature) {
        if (!enderChest()) throw new IllegalStateException("owned ender chest required");
        return firstDeposit(signature::matches);
    }

    public void observePlayer() {
        if (!world.available()) return;
        for (int i = 0; i < playerReceipts.length; i++) playerReceipts[i] = world.client().player.getInventory().getStack(i).copy();
    }

    public long playerVersion() { return playerVersion; }

    public int confirmedCount(Predicate<ItemStack> filter) {
        int count = 0;
        for (int i = 0; i < 36; i++) if (playerReceipts[i] != null && filter.test(playerReceipts[i])) count += playerReceipts[i].getCount();
        return count;
    }

    public boolean confirmedChest(ItemStack stack) {
        return playerReceipts[38] != null && ItemStack.areEqual(stack, playerReceipts[38]);
    }

    public int confirmedTotal(Predicate<ItemStack> filter) {
        int count = 0;
        for (ItemStack stack : playerReceipts) {
            if (stack != null && filter.test(stack)) count += stack.getCount();
        }
        return count;
    }

    public boolean containerCapacity(java.util.function.Predicate<ItemStack> filter) {
        if (!container()) return false;
        for (Slot from : handler.slots) if (from.inventory == world.client().player.getInventory() && !from.getStack().isEmpty() && filter.test(from.getStack())) {
            for (Slot to : handler.slots) if (to.inventory != from.inventory && to.canInsert(from.getStack()) && capacity(to, from.getStack()) > 0) return true;
        }
        return false;
    }

    public ItemStack sourceStack(InventoryTransaction transaction) {
        if (!ready() || handler.syncId != transaction.syncId()) throw new IllegalStateException("transaction source handler changed");
        return handler.slots.get(transaction.fromSlot()).getStack().copy();
    }

    public boolean playerSource(InventoryTransaction transaction) {
        if (!ready() || handler.syncId != transaction.syncId()) throw new IllegalStateException("transaction source handler changed");
        return handler.slots.get(transaction.fromSlot()).inventory == world.client().player.getInventory();
    }

    private void observePlayer(int index, ItemStack stack) {
        if (index < 0 || index >= playerReceipts.length) return;
        playerReceipts[index] = stack.copy();
        playerVersion++;
    }

    private void observeSlot(int syncId, int index, ItemStack stack) {
        if (!world.available()) return;
        ScreenHandler current = world.client().player.currentScreenHandler;
        if (current.syncId != syncId || index < 0 || index >= current.slots.size()) return;
        Slot slot = current.slots.get(index);
        if (slot.inventory == world.client().player.getInventory()) observePlayer(slot.getIndex(), stack);
    }

    private void observeContents(int syncId, List<ItemStack> contents) {
        for (int i = 0; i < contents.size(); i++) observeSlot(syncId, i, contents.get(i));
    }
    private static void require(Lease lease, Resource resource) {
        if (lease == null || lease.state() != LeaseState.GRANTED || !lease.resources().contains(resource)) {
            throw new IllegalStateException("inventory action without " + resource + " lease");
        }
    }

    private static String item(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static boolean matches(ItemStack stack, String id, int count) {
        return stack.getCount() == count && item(stack).equals(id);
    }

    public static final class Ticket {
        private final InventoryTransaction transaction;
        private final Lease lease;
        private long startedMs;
        private final int revision;
        private final Consumer<String> confirmed;
        private Stage stage = Stage.SENT;
        private List<ItemStack> before;
        private java.util.Map<Integer, ItemStack> received;

        private StackValue cursor;
        private long revokeAt;
        private boolean emittedReturn;
        private boolean serverConfirmed;
        private boolean partialStarted;
        private boolean partialReady;
        private boolean partialReturning;
        private int partialMoved;

        private Ticket(InventoryTransaction transaction, Lease lease, long startedMs, int revision, Consumer<String> confirmed) {
            this.transaction = transaction;
            this.lease = lease;
            this.startedMs = startedMs;
            this.revision = revision;
            this.confirmed = confirmed;
        }

        public boolean confirmed() { return serverConfirmed; }
        public void delay(long ms) { startedMs += ms; }
        public boolean failed() { return stage == Stage.QUARANTINED || stage == Stage.RETURNING || stage == Stage.RELEASED; }
        public boolean expired(long nowMs) { return stage == Stage.SENT && nowMs - startedMs >= 3_000L * (transaction.transferCount() == 0 ? 1 : transaction.transferCount() + 2); }
        private boolean finished() { return stage == Stage.RELEASED || stage == Stage.QUARANTINED; }
    }

    private record StackValue(String id, int count) {
        private static StackValue of(ItemStack stack) { return new StackValue(item(stack), stack.getCount()); }
    }

    private record CursorReceipt(WorldIdentity identity, long epoch, Object connection, StackValue stack) {
    }

    private enum Stage { SENT, CONFIRMED, RETURNING, RELEASED, QUARANTINED }
    private enum Emission { NONE, OWN }
}
