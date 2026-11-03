package com.hunterbuddy.modules.recast;

import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import com.hunterbuddy.util.GliderSwap;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;

public final class GliderWearer {
    private boolean retryPending;

    public void clearPending() {
        retryPending = false;
    }

    public void wearForRecovery(ClientPlayerEntity player) {
        wear(player);
        retryPending = !wearsGlider(player) && GliderSwap.hasGlider(player);
    }

    public void retryPendingWear(ClientPlayerEntity player) {
        if (!retryPending) return;
        if (wearsGlider(player) || !GliderSwap.hasGlider(player)) {
            retryPending = false;
            return;
        }
        wear(player);
    }

    private static boolean wearsGlider(ClientPlayerEntity player) {
        return GliderSwap.isGlider(player.getEquippedStack(EquipmentSlot.CHEST));
    }

    private static void wear(ClientPlayerEntity player) {
        PlayerInventoryAccessor inventory = (PlayerInventoryAccessor) player.getInventory();
        int selectedSlot = inventory.getSelectedSlot();
        GliderSwap.wearGlider(player);
        inventory.setSelectedSlot(selectedSlot);
    }
}
