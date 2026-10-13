package com.hunterbuddy.behavior.mc;

import java.util.Objects;
import net.minecraft.screen.slot.SlotActionType;

public record InventoryTransaction(int syncId, int fromSlot, int toSlot, String expectedItemId,
                                   int expectedCount, SlotActionType action) {
    public InventoryTransaction {
        Objects.requireNonNull(expectedItemId);
        Objects.requireNonNull(action);
        if (syncId <= 0 || fromSlot < 0 || toSlot < 0 || expectedItemId.isBlank() || expectedCount <= 0) {
            throw new IllegalArgumentException("invalid inventory transaction");
        }
        if (action != SlotActionType.SWAP && action != SlotActionType.PICKUP && action != SlotActionType.QUICK_MOVE) {
            throw new IllegalArgumentException("only validated SWAP, PICKUP and QUICK_MOVE transactions are supported");
        }
    }
}
