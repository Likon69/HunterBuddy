package com.hunterbuddy.modules.sorter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;

final class SorterDeliveryTracker {
    private record Cargo(int slot, ItemStack stack) { }

    private final SorterInventory inventory;
    private final List<ItemStack> purifiedCargo = new ArrayList<>();
    private final Map<String, Integer> byCategory = new LinkedHashMap<>();
    private final List<Cargo> beforeCargo = new ArrayList<>();
    private GenericContainerScreenHandler visitHandler;
    private List<ItemStack> beforeInventory;
    private List<ItemStack> beforeContainer;
    private boolean started;
    private String category;
    private int shulkers;
    private int purified;
    private int purifiedDelivered;
    private int looseStacks;
    private int packed;
    private int packedFull;

    SorterDeliveryTracker(SorterInventory inventory) {
        this.inventory = inventory;
    }

    void reset() {
        resetVisit();
        purifiedCargo.clear();
        byCategory.clear();
        shulkers = 0;
        purified = 0;
        purifiedDelivered = 0;
        looseStacks = 0;
        packed = 0;
        packedFull = 0;
    }

    void resetVisit() {
        started = false;
        category = null;
        visitHandler = null;
        beforeInventory = null;
        beforeContainer = null;
        beforeCargo.clear();
    }

    void begin(GenericContainerScreenHandler handler, String category, Predicate<ItemStack> accepts) {
        if (started) {
            return;
        }
        started = true;
        this.category = category;
        visitHandler = handler;
        beforeInventory = inventorySnapshot(handler);
        beforeContainer = containerSnapshot(handler);
        for (int inv = 0; inv < 36; inv++) {
            ItemStack stack = beforeInventory.get(inv);
            if (inventory.isCargoSlot(inv) && !stack.isEmpty() && accepts.test(stack)) {
                beforeCargo.add(new Cargo(inv, stack.copy()));
            }
        }
    }

    void finish(GenericContainerScreenHandler handler) {
        if (visitHandler != handler || !handler.getCursorStack().isEmpty()) {
            resetVisit();
            return;
        }
        List<ItemStack> afterInventory = inventorySnapshot(handler);
        List<ItemStack> afterContainer = containerSnapshot(handler);
        List<ItemStack> counted = new ArrayList<>();
        for (Cargo cargo : beforeCargo) {
            ItemStack stack = cargo.stack();
            if (counted.stream().anyMatch(item -> ItemStack.areItemsAndComponentsEqual(item, stack))) {
                continue;
            }
            counted.add(stack);
            int left = count(beforeInventory, stack) - count(afterInventory, stack);
            int stored = count(afterContainer, stack) - count(beforeContainer, stack);
            int confirmed = Math.min(left, stored);
            for (Cargo source : beforeCargo) {
                if (confirmed <= 0 || !ItemStack.areItemsAndComponentsEqual(source.stack(), stack)) {
                    continue;
                }
                ItemStack after = afterInventory.get(source.slot());
                int loss = source.stack().getCount()
                    - (ItemStack.areItemsAndComponentsEqual(source.stack(), after) ? after.getCount() : 0);
                int moved = Math.min(confirmed, Math.max(0, loss));
                if (moved == 0) {
                    continue;
                }
                confirmed -= moved;
                if (ShulkerSorter.isShulker(source.stack())) {
                    shulkers += moved;
                    for (int i = 0; i < moved; i++) {
                        for (int j = 0; j < purifiedCargo.size(); j++) {
                            if (ItemStack.areItemsAndComponentsEqual(purifiedCargo.get(j), source.stack())) {
                                purifiedCargo.remove(j);
                                purifiedDelivered++;
                                break;
                            }
                        }
                    }
                    byCategory.merge(category, moved, Integer::sum);
                } else {
                    looseStacks++;
                    byCategory.merge(category, 1, Integer::sum);
                }
            }
        }
        resetVisit();
    }

    void purified(ItemStack stack) {
        purified++;
        purifiedCargo.add(stack.copy());
    }

    void packed(boolean full) {
        packed++;
        if (full) {
            packedFull++;
        }
    }

    SorterRunSnapshot.Results results() {
        return new SorterRunSnapshot.Results(shulkers, purifiedDelivered, purified, looseStacks, packed, packedFull, byCategory);
    }

    private static int count(List<ItemStack> inventory, ItemStack stack) {
        int count = 0;
        for (ItemStack item : inventory) {
            if (ItemStack.areItemsAndComponentsEqual(item, stack)) {
                count += item.getCount();
            }
        }
        return count;
    }

    private static List<ItemStack> inventorySnapshot(GenericContainerScreenHandler handler) {
        List<ItemStack> stacks = new ArrayList<>(36);
        int slots = handler.getRows() * 9;
        for (int inv = 0; inv < 36; inv++) {
            stacks.add(handler.getSlot(SorterInventory.handlerSlotOf(slots, inv)).getStack().copy());
        }
        return stacks;
    }

    private static List<ItemStack> containerSnapshot(GenericContainerScreenHandler handler) {
        List<ItemStack> stacks = new ArrayList<>(handler.getRows() * 9);
        for (int slot = 0; slot < handler.getRows() * 9; slot++) {
            stacks.add(handler.getSlot(slot).getStack().copy());
        }
        return stacks;
    }
}
