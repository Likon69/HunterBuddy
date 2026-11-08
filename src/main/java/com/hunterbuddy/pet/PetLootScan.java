package com.hunterbuddy.pet;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;

public final class PetLootScan {
    private PetLootScan() {
    }

    public static boolean storage(ScreenHandler handler) {
        return handler instanceof GenericContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler;
    }

    public static boolean holdsValuables(ScreenHandler handler, Inventory player) {
        for (Slot slot : handler.slots) {
            if (slot.inventory == player) continue;
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && PetLoot.valuable(Registries.ITEM.getId(stack.getItem()).getPath())) return true;
        }
        return false;
    }
}
