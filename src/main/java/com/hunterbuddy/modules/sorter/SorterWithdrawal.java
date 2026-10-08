package com.hunterbuddy.modules.sorter;

import com.hunterbuddy.modules.sorter.logic.CategoryRules;
import com.hunterbuddy.modules.sorter.model.SorterProfile;
import com.hunterbuddy.modules.sorter.model.SorterProfile.Pos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import static meteordevelopment.meteorclient.MeteorClient.mc;

final class SorterWithdrawal {
    record Request(String category, int count, boolean ender, boolean fullOnly) { }

    private final SorterInventory inventory;
    private final Request request;
    private final List<Pos> sources;
    private final Set<Pos> exhausted = new HashSet<>();
    private final Map<Pos, Integer> visits = new LinkedHashMap<>();
    private final Map<Integer, ItemStack> taken = new LinkedHashMap<>();
    private final Set<Integer> pendingDeposits = new HashSet<>();
    private final SorterDeliveryTracker deposits;
    private final int visitLimit;
    private final Pos enderChest;
    private ItemStack pending = ItemStack.EMPTY;
    private int pendingSlot = -1;
    private int pendingSource = -1;
    private GenericContainerScreenHandler pendingHandler;
    private int deposited;
    private int withdrawn;

    SorterWithdrawal(SorterInventory inventory, Request request, SorterProfile profile) {
        this.inventory = inventory;
        this.request = request;
        deposits = new SorterDeliveryTracker(inventory);
        this.enderChest = profile.enderChest;
        Set<Pos> ordered = new LinkedHashSet<>();
        profile.destinationsFor(request.category()).forEach(destination -> ordered.addAll(destination.chests));
        sources = new ArrayList<>(ordered);
        int capacity = Math.max(1, inventory.freeCargoSlots());
        visitLimit = (request.count() + capacity - 1) / capacity + 1;
    }

    Request request() {
        return request;
    }

    Pos nextSource(Set<Pos> excluded) {
        for (Pos pos : sources) {
            if (!exhausted.contains(pos) && !excluded.contains(pos) && visit(pos)) {
                return pos;
            }
        }
        return null;
    }

    Pos nextEnder(Set<Pos> excluded) {
        return enderChest != null && !excluded.contains(enderChest) && visit(enderChest) ? enderChest : null;
    }

    boolean visit(Pos pos) {
        int count = visits.getOrDefault(pos, 0);
        if (count >= visitLimit) {
            return false;
        }
        visits.put(pos, count + 1);
        return true;
    }

    private boolean eligible(ItemStack stack) {
        if (!ShulkerSorter.isShulker(stack) || stack.getCount() != 1) {
            return false;
        }
        if (CategoryRules.SHULKERS.equals(request.category())) {
            return PackingPlan.empty(stack);
        }
        List<ItemStack> contents = PackingPlan.contents(stack);
        return PackingPlan.pure(contents, request.category()) && (!request.fullOnly() || PackingPlan.full(contents));
    }

    boolean take(GenericContainerScreenHandler handler, Pos pos) {
        settle(handler);
        if (!pending.isEmpty()) {
            if (handler != pendingHandler || !ItemStack.areEqual(handler.getCursorStack(), pending)
                || !handler.getSlot(pendingSlot).getStack().isEmpty()
                || inventory.firstEmptyCargoSlot(handler) != pendingSlot) {
                return false;
            }
            if (!prepareCargoSlot(handler, pendingSlot)) {
                return false;
            }
            inventory.emptyCursor(handler);
            settle(handler);
            return true;
        }
        if (!handler.getCursorStack().isEmpty() || withdrawn() >= request.count()) {
            return false;
        }
        int destination = inventory.firstEmptyCargoSlot(handler);
        if (destination < 0 || inventory.openingClicks + 2 > SorterInventory.clickLimit(handler)
            || !prepareCargoSlot(handler, destination)) {
            return false;
        }
        for (int source = 0; source < handler.getRows() * 9; source++) {
            ItemStack stack = handler.getSlot(source).getStack();
            if (!eligible(stack)) {
                continue;
            }
            pending = stack.copy();
            pendingSlot = destination;
            pendingSource = source;
            pendingHandler = handler;
            inventory.cursorSourceSlot = source;
            mc.interactionManager.clickSlot(handler.syncId, source, 0, SlotActionType.PICKUP, mc.player);
            inventory.openingClicks++;
            return true;
        }
        exhausted.add(pos);
        return false;
    }

    void settle(GenericContainerScreenHandler handler) {
        if (pending.isEmpty() || handler != pendingHandler || !handler.getCursorStack().isEmpty()) {
            return;
        }
        if (handler.getSlot(pendingSource).getStack().isEmpty()
            && ItemStack.areEqual(handler.getSlot(pendingSlot).getStack(), pending)) {
            int inv = inventorySlot(handler, pendingSlot);
            if (!pendingDeposits.contains(inv) && !taken.containsKey(inv)) {
                taken.put(inv, pending.copy());
            }
        }
        pending = ItemStack.EMPTY;
        pendingHandler = null;
    }

    private boolean prepareCargoSlot(GenericContainerScreenHandler handler, int slot) {
        int inv = inventorySlot(handler, slot);
        confirmEmptyDeposit(inv);
        return !pendingDeposits.contains(inv) && !taken.containsKey(inv);
    }

    private static int inventorySlot(GenericContainerScreenHandler handler, int slot) {
        int inv = slot - handler.getRows() * 9;
        return inv < 27 ? inv + 9 : inv - 27;
    }

    boolean owns(int inv, ItemStack stack) {
        return taken.containsKey(inv) && ItemStack.areEqual(taken.get(inv), stack);
    }

    boolean cargoChanged() {
        for (Map.Entry<Integer, ItemStack> entry : taken.entrySet()) {
            ItemStack current = mc.player.getInventory().getStack(entry.getKey());
            if (!ItemStack.areEqual(entry.getValue(), current)
                && !(pendingDeposits.contains(entry.getKey()) && current.isEmpty())) {
                return true;
            }
        }
        return false;
    }

    void beginDeposit(GenericContainerScreenHandler handler) {
        deposits.begin(handler, request.category(), this::owns);
    }

    void depositClicked(int inv) {
        if (taken.containsKey(inv) && mc.player.getInventory().getStack(inv).isEmpty()) {
            pendingDeposits.add(inv);
        }
    }

    void finishDeposit(GenericContainerScreenHandler handler) {
        for (Map.Entry<Integer, Integer> entry : deposits.finish(handler).entrySet()) {
            if (taken.remove(entry.getKey()) != null) {
                deposited += entry.getValue();
            }
        }
        pendingDeposits.clear();
    }

    void finishClosedDeposit() {
        deposits.resetVisit();
        for (int inv : new HashSet<>(pendingDeposits)) {
            confirmEmptyDeposit(inv);
        }
        pendingDeposits.clear();
    }

    private void confirmEmptyDeposit(int inv) {
        if (pendingDeposits.contains(inv) && mc.player.getInventory().getStack(inv).isEmpty()) {
            if (taken.remove(inv) != null) {
                deposited++;
            }
            pendingDeposits.remove(inv);
        }
    }

    int held() {
        int count = 0;
        for (Map.Entry<Integer, ItemStack> entry : taken.entrySet()) {
            if (owns(entry.getKey(), mc.player.getInventory().getStack(entry.getKey()))) {
                count++;
            }
        }
        return count;
    }

    int withdrawn() {
        int pendingCount = 0;
        for (int inv : pendingDeposits) {
            if (taken.containsKey(inv) && mc.player.getInventory().getStack(inv).isEmpty()) {
                pendingCount++;
            }
        }
        withdrawn = Math.max(withdrawn, deposited + held() + pendingCount);
        return withdrawn;
    }

    String summary() {
        return withdrawn() + "/" + request.count() + " withdrawn, " + held() + " left in inventory";
    }

    SorterRunSnapshot.Withdrawal snapshot() {
        return new SorterRunSnapshot.Withdrawal(request.category(), request.count(), withdrawn(),
            request.ender() ? "Ender chest" : "Inventory", held());
    }
}
