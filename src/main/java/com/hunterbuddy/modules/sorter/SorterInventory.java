package com.hunterbuddy.modules.sorter;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import static meteordevelopment.meteorclient.MeteorClient.mc;

final class SorterInventory {
    private final ShulkerSorter owner;
    private final BooleanSupplier useHotbar;
    private final Set<Integer> protectedSlots = new HashSet<>();
    private final Set<Integer> recoveredSlots = new HashSet<>();
    int reservedSlot = -1;
    int openingClicks;
    private int recoveryClicks;
    int cursorSourceSlot = -1;
    boolean closingRequested;

    SorterInventory(ShulkerSorter owner, BooleanSupplier useHotbar) {
        this.owner = owner;
        this.useHotbar = useHotbar;
    }

    void reset() {
        protectedSlots.clear();
        recoveredSlots.clear();
        reservedSlot = -1;
        for (int i = 0; i < 36; i++) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
                protectedSlots.add(i);
            }
        }
        cursorSourceSlot = -1;
    }

    void reserveHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                reservedSlot = i;
                break;
            }
        }
    }

    int cargoCapacity() {
        int capacity = 0;
        for (int inv = 0; inv < 36; inv++) if (inv != reservedSlot && isCargoSlot(inv)) capacity++;
        return capacity;
    }

    static int handlerSlotOf(int containerSlots, int inv) {
        return inv < 9 ? containerSlots + 27 + inv : containerSlots + (inv - 9);
    }

    static int clickLimit(ScreenHandler handler) {
        return 2 * handler.slots.size();
    }

    void resetClicks() {
        openingClicks = 0;
        recoveryClicks = 0;
        cursorSourceSlot = -1;
        closingRequested = false;
    }

    void open() {
        openingClicks = 0;
        recoveryClicks = 0;
        closingRequested = false;
        if (mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
            cursorSourceSlot = -1;
        }
    }

    boolean isCargoSlot(int inv) {
        if (recoveredSlots.contains(inv)) {
            if (ShulkerSorter.isShulker(mc.player.getInventory().getStack(inv))) {
                return true;
            }
            recoveredSlots.remove(inv);
        }
        return !protectedSlots.contains(inv) && (inv >= 9 || useHotbar.getAsBoolean() || inv == reservedSlot);
    }

    void acceptRecovered(int slot) {
        recoveredSlots.add(slot);
    }

    int freeCargoSlots() {
        int free = 0;
        for (int i = 0; i < 36; i++) {
            if (i != reservedSlot && isCargoSlot(i) && mc.player.getInventory().getStack(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    int firstEmptyCargoSlot(ScreenHandler handler) {
        int containerSlots = handler.slots.size() - 36;
        for (int inv = 9; inv < 36; inv++) {
            int slot = handlerSlotOf(containerSlots, inv);
            if (inv != reservedSlot && isCargoSlot(inv) && handler.getSlot(slot).getStack().isEmpty()) {
                return slot;
            }
        }
        for (int inv = 0; inv < 9; inv++) {
            int slot = handlerSlotOf(containerSlots, inv);
            if (inv != reservedSlot && isCargoSlot(inv) && handler.getSlot(slot).getStack().isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    boolean emptyCursor(ScreenHandler handler) {
        if (recoveryClicks >= clickLimit(handler)) {
            owner.stopWithCursor();
            return false;
        }
        int slot = firstEmptyCargoSlot(handler);
        if (slot == -1) {
            slot = cursorSourceSlot;
            closingRequested = true;
        }
        if (slot < 0 || slot >= handler.slots.size()) {
            owner.stopWithCursor();
            return false;
        }
        mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        openingClicks++;
        if (closingRequested) {
            recoveryClicks++;
            if (handler.getCursorStack().isEmpty()) {
                if (!owner.isPurifying()) owner.closeThen();
                return false;
            }
        }
        return true;
    }

}
