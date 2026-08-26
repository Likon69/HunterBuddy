package com.hunterbuddy.bephax;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/** The reference's firework helper: use one where it lies, off-hand included, and swap back. */
public final class BepUtils {
    private BepUtils() {
    }

    public static int firework(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return -1;

        FindItemResult found = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);

        if (found.found()) {
            if (found.isOffhand()) {
                mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                mc.player.swingHand(Hand.OFF_HAND);
            }
            else {
                InvUtils.swap(found.slot(), true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.swapBack();
            }

            return 200;
        }

        int selected = mc.player.getInventory().getSelectedSlot();

        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.FIREWORK_ROCKET)) {
                InvUtils.move().from(i).toHotbar(selected);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                InvUtils.move().from(selected).to(i);

                return 200;
            }
        }

        return -1;
    }
}
