package com.hunterbuddy.modules.recast;

import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.util.QuietClicks;
import com.hunterbuddy.util.Rockets;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class RocketLauncher {
    private final MinecraftClient mc = MeteorClient.mc;
    private boolean waitingForQuietClick;

    public boolean isWaiting() {
        return waitingForQuietClick;
    }

    public boolean use() {
        waitingForQuietClick = false;
        if (!canFire()) return false;
        FindItemResult rocket = hotbarRocket();
        if (!rocket.found() && stageFromInventory()) rocket = hotbarRocket();
        if (!rocket.found()) return false;
        return fire(rocket);
    }

    private boolean canFire() {
        ClientPlayerEntity player = mc.player;
        return player != null && mc.interactionManager != null && player.isGliding();
    }

    private static FindItemResult hotbarRocket() {
        return InvUtils.findInHotbar(Rockets::isPlain);
    }

    private boolean stageFromInventory() {
        FindItemResult stored = InvUtils.find(Rockets::isPlain);
        FindItemResult emptySlot = InvUtils.findInHotbar(ItemStack::isEmpty);
        if (!stored.found() || !emptySlot.found()) return false;
        if (!QuietClicks.readyOrSettle()) {
            waitingForQuietClick = true;
            return false;
        }
        InvUtils.move().from(stored.slot()).to(emptySlot.slot());
        return true;
    }

    private boolean fire(FindItemResult rocket) {
        if (rocket.isOffhand()) return interact(Hand.OFF_HAND);
        if (Rockets.isPlain(mc.player.getMainHandStack())) return interact(Hand.MAIN_HAND);
        return Utils.firework(mc, false) != -1;
    }

    private boolean interact(Hand hand) {
        mc.interactionManager.interactItem(mc.player, hand);
        mc.player.swingHand(hand);
        return true;
    }
}
