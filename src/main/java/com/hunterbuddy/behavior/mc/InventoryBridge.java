package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.resource.GatePolicy;
import com.hunterbuddy.behavior.resource.Lease;
import com.hunterbuddy.behavior.resource.LeaseState;
import com.hunterbuddy.behavior.resource.Resource;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
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
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
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
    private volatile WorldIdentity identity;
    private volatile long epoch;
    private BlockPos requested;
    private long requestedAt;
    private Ticket ticket;
    private Emission emission = Emission.NONE;
    private String quarantine;
    private long suspendedAt = -1;
    private final Deque<CursorReceipt> cursors = new ArrayDeque<>();

    public InventoryBridge(WorldReader world, LongSupplier ticks, Consumer<String> alert) {
        this.world = Objects.requireNonNull(world);
        this.ticks = Objects.requireNonNull(ticks);
        this.alert = Objects.requireNonNull(alert);
    }

    public boolean open(BlockPos pos, Lease lease) {
        require(lease, Resource.SCREEN);
        require(lease, Resource.INTERACT);
        require(lease, Resource.LOOK);
        if (isOurs()) return true;
        if (quarantine != null || !world.available() || world.client().currentScreen != null || !world.loaded(pos)) return false;
        if (requested != null) return false;
        if (!(world.client().crosshairTarget instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)
            || world.client().player.getEyePos().squaredDistanceTo(hit.getPos()) > 4.5 * 4.5) return false;
        identity = world.identity();
        requested = pos.toImmutable();
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
        return isOurs() && handler.syncId > 0;
    }

    public InventoryTransaction firstTransfer() {
        if (!ready() || !(handler instanceof GenericContainerScreenHandler container)) {
            throw new IllegalStateException("owned chest is not synchronized");
        }
        for (Slot from : handler.slots) {
            if (from.inventory != container.getInventory() || from.getStack().isEmpty() || !from.canTakeItems(world.client().player)) continue;
            for (Slot to : handler.slots) {
                if (to.inventory != world.client().player.getInventory() || to.getIndex() < 0 || to.getIndex() > 8 || !to.getStack().isEmpty()) continue;
                return new InventoryTransaction(handler.syncId, from.id, to.id, item(from.getStack()), from.getStack().getCount(), SlotActionType.SWAP);
            }
        }
        throw new IllegalStateException("chest needs an item and an empty player hotbar slot");
    }

    public InventoryTransaction firstDeposit(Predicate<ItemStack> filter) {
        if (!ready() || !(handler instanceof GenericContainerScreenHandler container)) {
            throw new IllegalStateException("owned chest is not synchronized");
        }
        for (Slot from : handler.slots) {
            if (from.inventory != world.client().player.getInventory() || from.getStack().isEmpty()
                || !filter.test(from.getStack()) || !from.canTakeItems(world.client().player)) continue;
            for (Slot to : handler.slots) {
                if (to.inventory != container.getInventory() || !to.getStack().isEmpty() || !to.canInsert(from.getStack())) continue;
                return new InventoryTransaction(handler.syncId, from.id, to.id, item(from.getStack()), from.getStack().getCount(), SlotActionType.QUICK_MOVE);
            }
        }
        throw new IllegalStateException("chest has no free space for a deposit");
    }

    public Ticket click(InventoryTransaction transaction, Lease lease, long nowMs, Consumer<String> confirmed) {
        require(lease, Resource.INVENTORY);
        require(lease, Resource.SCREEN);
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
            || to.getIndex() < 0 || to.getIndex() > 8 || !to.getStack().isEmpty() || !to.canInsert(from.getStack()))) {
            throw new IllegalStateException("SWAP requires an empty hotbar destination");
        }
        if (transaction.action() == SlotActionType.QUICK_MOVE && from.inventory != world.client().player.getInventory()) {
            throw new IllegalStateException("QUICK_MOVE deposits only from the player inventory");
        }
        epoch++;
        ticket = new Ticket(transaction, lease, nowMs, handler.getRevision(), Objects.requireNonNull(confirmed));
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

    public boolean close() {
        if (!isOurs()) return false;
        if (quarantine != null || !handler.getCursorStack().isEmpty() || ticket != null && !ticket.finished()) return false;
        world.client().player.closeHandledScreen();
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
        if (!isOurs()) {
            quarantine("inventory handler or player authority changed");
            return true;
        }
        if (ticket.transaction.action() != SlotActionType.PICKUP && handler.getCursorStack().isEmpty()) {
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
                || !from.getStack().isEmpty() || !from.canInsert(handler.getCursorStack())) {
                quarantine("cursor cannot be returned safely");
                return true;
            }
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
        if (ticket != null && !ticket.finished()) quarantine("session ended during an inventory transaction");
        forgetScreen();
        synchronized (cursors) { cursors.clear(); }
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
        screen = null;
        requested = null;
    }

    private void claim() {
        if (requested == null || !world.available() || world.legacyRegear() || !identity.equals(world.identity())) return;
        ScreenHandler candidate = world.client().player.currentScreenHandler;
        if (candidate instanceof GenericContainerScreenHandler && candidate.syncId > 0 && world.client().currentScreen instanceof HandledScreen<?>) {
            handler = candidate;
            screen = world.client().currentScreen;
            requested = null;
        }
    }

    @EventHandler
    private void inventory(InventoryEvent event) {
        claim();
        if (!receiptAuthority() || event.packet.syncId() != ticket.transaction.syncId() || event.packet.revision() <= ticket.revision) return;
        for (int slot = 0; slot < event.packet.contents().size(); slot++) acceptSlot(slot, event.packet.contents().get(slot));
        acceptCursor(StackValue.of(event.packet.cursorStack()));
    }

    @EventHandler
    private void slot(ContainerSlotUpdateEvent event) {
        if (receiptAuthority() && event.packet.getSyncId() == ticket.transaction.syncId() && event.packet.getRevision() > ticket.revision) {
            acceptSlot(event.packet.getSlot(), event.packet.getStack());
        }
    }

    private boolean receiptAuthority() {
        return ticket != null && world.available() && identity != null && identity.equals(world.identity());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void receive(PacketEvent.Receive event) {
        if (event.isCancelled() || !(event.packet instanceof SetCursorItemS2CPacket packet)) return;
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
            if (ticket != null && click.syncId() == ticket.transaction.syncId() && !ticket.finished()) quarantine("another actor clicked the inventory");
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
        if (slot == ticket.transaction.fromSlot()) ticket.sourceEmpty = stack.isEmpty();
        if (slot == ticket.transaction.toSlot()) ticket.destination = matches(stack, ticket.transaction.expectedItemId(), ticket.transaction.expectedCount());
        confirm();
    }

    private void acceptCursor(StackValue cursor) {
        if (ticket == null) return;
        ticket.cursor = cursor;
        confirm();
    }

    private void confirm() {
        if (ticket.stage != Stage.SENT || !ticket.sourceEmpty) return;
        boolean received = switch (ticket.transaction.action()) {
            case SWAP -> ticket.destination;
            case QUICK_MOVE -> true;
            default -> ticket.cursor != null && ticket.cursor.id.equals(ticket.transaction.expectedItemId()) && ticket.cursor.count == ticket.transaction.expectedCount();
        };
        if (received) {
            ticket.stage = Stage.CONFIRMED;
            ticket.serverConfirmed = true;
            ticket.confirmed.accept("TRANSACTION_CONFIRMED syncId=" + ticket.transaction.syncId() + " from=" + ticket.transaction.fromSlot() + " to=" + ticket.transaction.toSlot());
        }
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
        return !stack.isEmpty() && stack.getCount() == count && item(stack).equals(id);
    }

    public static final class Ticket {
        private final InventoryTransaction transaction;
        private final Lease lease;
        private long startedMs;
        private final int revision;
        private final Consumer<String> confirmed;
        private Stage stage = Stage.SENT;
        private boolean sourceEmpty;
        private boolean destination;
        private StackValue cursor;
        private long revokeAt;
        private boolean emittedReturn;
        private boolean serverConfirmed;

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
        public boolean expired(long nowMs) { return stage == Stage.SENT && nowMs - startedMs >= 3_000; }
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
