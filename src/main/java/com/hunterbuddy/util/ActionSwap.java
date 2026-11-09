package com.hunterbuddy.util;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import meteordevelopment.meteorclient.mixininterface.IClientPlayerInteractionManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.screen.slot.SlotActionType;

public final class ActionSwap {
    private static boolean active;

    private ActionSwap() {}

    public static boolean active() {
        return active;
    }

    public static boolean canSwapInventory() {
        return mc.player != null && mc.currentScreen == null
            && mc.player.currentScreenHandler == mc.player.playerScreenHandler
            && mc.player.playerScreenHandler.getCursorStack().isEmpty();
    }

    public static void run(int slot, boolean inventorySwap, Runnable action) {
        ClientPlayerEntity player = mc.player;
        ClientPlayerInteractionManager manager = mc.interactionManager;
        if (player == null || manager == null || mc.getNetworkHandler() == null || slot < 0 || slot > 8) return;

        int origin = player.getInventory().getSelectedSlot();
        if (slot == origin) {
            action.run();
            return;
        }

        if (inventorySwap && canSwapInventory()) {
            int syncId = player.playerScreenHandler.syncId;
            Runnable swap = () -> manager.clickSlot(syncId, 36 + slot, origin, SlotActionType.SWAP, player);
            perform(swap, action, swap);
        } else {
            perform(() -> select(player, manager, slot), action, () -> select(player, manager, origin));
        }
    }

    static void perform(Runnable equip, Runnable action, Runnable restore) {
        boolean previous = active;
        active = true;
        try {
            equip.run();
            action.run();
        } finally {
            try {
                restore.run();
            } finally {
                active = previous;
            }
        }
    }

    private static void select(ClientPlayerEntity player, ClientPlayerInteractionManager manager, int slot) {
        player.getInventory().setSelectedSlot(slot);
        ((IClientPlayerInteractionManager) manager).meteor$syncSelected();
    }
}
