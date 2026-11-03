package com.hunterbuddy.modules.regear.util;

import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import com.hunterbuddy.util.QuietClicks;
import com.hunterbuddy.util.Rockets;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;

final class FireworkUse {
    private static final int FAILED = -1;
    private static final int NO_SWAP = 200;
    private static final int NO_ELYTRA = -1;
    private static final int CHEST_ARMOR_SLOT = 38;
    private static final int FIRST_STORAGE_SLOT = 9;

    private FireworkUse() {
    }

    static int use(MinecraftClient mc, boolean elytraRequired) {
        if (mc.player == null || mc.interactionManager == null) return FAILED;
        int elytraSlot = NO_ELYTRA;
        if (elytraRequired && !mc.player.getInventory().getStack(CHEST_ARMOR_SLOT).isOf(Items.ELYTRA)) {
            elytraSlot = equipElytra(mc);
            if (elytraSlot == NO_ELYTRA) return FAILED;
        }
        if (!useRocket(mc)) return FAILED;
        return elytraSlot == NO_ELYTRA ? NO_SWAP : elytraSlot;
    }

    private static int equipElytra(MinecraftClient mc) {
        FindItemResult elytra = InvUtils.findInHotbar(Items.ELYTRA);
        if (!elytra.found()) return NO_ELYTRA;
        InvUtils.swap(elytra.slot(), true);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        InvUtils.swapBack();
        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        return elytra.slot();
    }

    private static boolean useRocket(MinecraftClient mc) {
        FindItemResult rocket = InvUtils.findInHotbar(Rockets::isPlain);
        if (rocket.found()) {
            useHotbarRocket(mc, rocket);
            return true;
        }
        return useStoredRocket(mc);
    }

    private static void useHotbarRocket(MinecraftClient mc, FindItemResult rocket) {
        if (rocket.isOffhand()) {
            interact(mc, Hand.OFF_HAND);
            return;
        }
        InvUtils.swap(rocket.slot(), true);
        interact(mc, Hand.MAIN_HAND);
        InvUtils.swapBack();
    }

    private static boolean useStoredRocket(MinecraftClient mc) {
        PlayerInventoryAccessor accessor = (PlayerInventoryAccessor) mc.player.getInventory();
        for (int slot = FIRST_STORAGE_SLOT; slot < accessor.getMain().size(); slot++) {
            if (!Rockets.isPlain(accessor.getMain().get(slot))) continue;
            if (!QuietClicks.readyOrSettle()) return false;
            fireThroughSelectedSlot(mc, slot, accessor.getSelectedSlot());
            return true;
        }
        return false;
    }

    private static void fireThroughSelectedSlot(MinecraftClient mc, int from, int selected) {
        InvUtils.move().from(from).to(selected);
        interact(mc, Hand.MAIN_HAND);
        InvUtils.move().from(selected).to(from);
    }

    private static void interact(MinecraftClient mc, Hand hand) {
        mc.interactionManager.interactItem(mc.player, hand);
        mc.player.swingHand(hand);
    }
}
