/*
 * Port of com.lambda.interaction.handlers.ContainerHandler (lambda 1.21.11).
 * Minimal structural port — findDisposable scans the player's inventory
 * for an item matching inventoryConfig.disposables. Falls back to null
 * which then triggers the NETHERRACK default in TargetState.Solid.
 *
 * Full MaterialContainer/ContainerSelection port is deferred — the
 * findDisposable path used by TargetState.Solid/Support.getStack()
 * is fully covered by the inventory scan.
 */
package com.hunterbuddy.lambda;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.List;

public final class ContainerHandler {
    public static class NoContainerFound extends RuntimeException {
        public NoContainerFound(String message) { super(message); }
    }

    /** Scans the player's inventory (open container first, then inventory)
     *  for an item matching the disposables list. Returns null if none. */
    public static ItemStack findDisposable(List<Item> disposables) {
        if (disposables == null || disposables.isEmpty()) return null;
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return null;
        PlayerEntity p = mc.player;

        // Open container first (chests, shulker, etc.)
        if (mc.player.currentScreenHandler != null
                && !(mc.player.currentScreenHandler instanceof net.minecraft.screen.PlayerScreenHandler)) {
            int count = mc.player.currentScreenHandler.slots.size();
            for (int i = 0; i < count; i++) {
                var slot = mc.player.currentScreenHandler.getSlot(i);
                ItemStack s = slot.getStack();
                if (disposables.contains(s.getItem())) return s.copy();
            }
        }

        // Then the player inventory itself
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (disposables.contains(s.getItem())) return s.copy();
        }
        return null;
    }

    private ContainerHandler() {}
}
