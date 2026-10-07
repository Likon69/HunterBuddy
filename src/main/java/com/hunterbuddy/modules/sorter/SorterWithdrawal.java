package com.hunterbuddy.modules.sorter;

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
    private final SorterProfile profile;
    private final List<Pos> sources;
    private final Set<Pos> exhausted = new HashSet<>();
    private final Map<Pos, Integer> visits = new LinkedHashMap<>();
    private final Map<Integer, ItemStack> taken = new LinkedHashMap<>();
    private final int visitLimit;
    private final Pos enderChest;
    private ItemStack pending = ItemStack.EMPTY;
    private int pendingSlot = -1;
    private int pendingSource = -1;
    private GenericContainerScreenHandler pendingHandler;
    private int deposited;

    SorterWithdrawal(SorterInventory inventory, Request request, SorterProfile profile) {
        this.inventory = inventory;
        this.request = request;
        this.profile = profile;
        this.enderChest = profile.enderChest;
        Set<Pos> ordered = new LinkedHashSet<>();
        profile.destinationsFor(request.category()).forEach(destination -> ordered.addAll(destination.chests));
        sources = new ArrayList<>(ordered);
        int capacity = Math.max(1, inventory.freeCargoSlots());
        visitLimit = (request.count() + capacity - 1) / capacity + 1;
    }

    boolean matchesProfile(SorterProfile current) {
        return profile == current;
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
            inventory.emptyCursor(handler);
            settle(handler);
            return true;
        }
        if (!handler.getCursorStack().isEmpty() || withdrawn() >= request.count()) {
            return false;
        }
        int destination = inventory.firstEmptyCargoSlot(handler);
        if (destination < 0 || inventory.openingClicks + 2 > SorterInventory.clickLimit(handler)) {
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
            int containerSlots = handler.getRows() * 9;
            int inv = pendingSlot - containerSlots;
            inv = inv < 27 ? inv + 9 : inv - 27;
            taken.put(inv, pending.copy());
        }
        pending = ItemStack.EMPTY;
        pendingHandler = null;
    }

    boolean owns(int inv, ItemStack stack) {
        return taken.containsKey(inv) && ItemStack.areEqual(taken.get(inv), stack);
    }

    boolean cargoChanged() {
        for (Map.Entry<Integer, ItemStack> entry : taken.entrySet()) {
            if (!ItemStack.areEqual(entry.getValue(), mc.player.getInventory().getStack(entry.getKey()))) {
                return true;
            }
        }
        return false;
    }

    void deposited(int inv) {
        if (mc.player.getInventory().getStack(inv).isEmpty() && taken.remove(inv) != null) {
            deposited++;
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
        return deposited + held();
    }

    String summary() {
        return withdrawn() + "/" + request.count() + " withdrawn, " + held() + " left in inventory";
    }

    SorterRunSnapshot.Withdrawal snapshot() {
        return new SorterRunSnapshot.Withdrawal(request.category(), request.count(), withdrawn(),
            request.ender() ? "Ender chest" : "Inventory", held());
    }
}
