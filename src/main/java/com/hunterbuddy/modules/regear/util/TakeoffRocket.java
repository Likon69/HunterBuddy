package com.hunterbuddy.modules.regear.util;

import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import com.hunterbuddy.util.Rockets;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;

final class TakeoffRocket {
    private static final int LAST_HOTBAR_SLOT = 8;

    private TakeoffRocket() {
    }

    static boolean use(MinecraftClient mc, int hotbarSlot) {
        if (mc.interactionManager == null) return false;
        if (hotbarSlot >= 0 && hotbarSlot <= LAST_HOTBAR_SLOT) {
            ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(hotbarSlot);
        }
        FindItemResult rocket = InvUtils.findInHotbar(Rockets::isPlain);
        if (!rocket.found()) return false;
        fire(mc, rocket);
        return true;
    }

    private static void fire(MinecraftClient mc, FindItemResult rocket) {
        if (rocket.isOffhand()) {
            interact(mc, Hand.OFF_HAND);
        } else if (rocket.isMainHand()) {
            interact(mc, Hand.MAIN_HAND);
        } else {
            InvUtils.swap(rocket.slot(), true);
            interact(mc, Hand.MAIN_HAND);
            InvUtils.swapBack();
        }
    }

    private static void interact(MinecraftClient mc, Hand hand) {
        mc.interactionManager.interactItem(mc.player, hand);
        mc.player.swingHand(hand);
    }
}
