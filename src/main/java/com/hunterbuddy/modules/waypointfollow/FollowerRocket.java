package com.hunterbuddy.modules.waypointfollow;

import com.hunterbuddy.util.QuietClicks;
import com.hunterbuddy.util.Rockets;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.Hand;

public final class FollowerRocket {
    private static final int COOLDOWN_TICKS = 10;
    private static final int HOTBAR_END = 9;
    private static final int MAIN_END = 36;

    private final MinecraftClient mc = MeteorClient.mc;
    private int cooldown;

    public void tick() {
        if (cooldown > 0) cooldown--;
    }

    public void reset() {
        cooldown = 0;
    }

    public void use() {
        if (mc.player == null || mc.interactionManager == null || cooldown > 0) return;
        int hotbar = findPlain(0, HOTBAR_END);
        if (hotbar >= 0) {
            fireFromHotbar(hotbar);
            return;
        }
        int stored = findPlain(HOTBAR_END, MAIN_END);
        if (stored >= 0 && QuietClicks.readyOrSettle()) fireFromInventory(stored);
    }

    private int findPlain(int from, int to) {
        PlayerInventory inventory = mc.player.getInventory();
        for (int slot = from; slot < to; slot++) {
            if (Rockets.isPlain(inventory.getStack(slot))) return slot;
        }
        return -1;
    }

    private void fireFromHotbar(int slot) {
        InvUtils.swap(slot, true);
        interact();
        InvUtils.swapBack();
    }

    private void fireFromInventory(int slot) {
        int selected = mc.player.getInventory().getSelectedSlot();
        InvUtils.move().from(slot).to(selected);
        interact();
        InvUtils.move().from(selected).to(slot);
    }

    private void interact() {
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        cooldown = COOLDOWN_TICKS;
    }
}
