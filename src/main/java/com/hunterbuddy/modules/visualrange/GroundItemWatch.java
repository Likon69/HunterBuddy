package com.hunterbuddy.modules.visualrange;

import com.hunterbuddy.util.HuntFeed;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public final class GroundItemWatch {
    private static final long MILLIS_PER_SECOND = 1000L;

    private final GroundItemSettings settings;
    private final DiscordSettings discord;
    private final AlertSink alerts;
    private final Map<Integer, Long> lastNotified = new HashMap<>();

    public GroundItemWatch(GroundItemSettings settings, DiscordSettings discord, AlertSink alerts) {
        this.settings = settings;
        this.discord = discord;
        this.alerts = alerts;
    }

    public void clear() {
        lastNotified.clear();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long cooldown = settings.itemCooldown.get() * MILLIS_PER_SECOND;
        lastNotified.values().removeIf(at -> now - at > cooldown);
        if (settings.trackedItems.get().isEmpty()) return;
        for (Entity entity : MeteorClient.mc.world.getEntities()) {
            if (entity instanceof ItemEntity item && isNewSighting(item)) announce(item, now);
        }
    }

    private boolean isNewSighting(ItemEntity item) {
        return settings.trackedItems.get().contains(item.getStack().getItem())
            && !lastNotified.containsKey(item.getId());
    }

    private void announce(ItemEntity item, long now) {
        lastNotified.put(item.getId(), now);
        ItemStack stack = item.getStack();
        String name = stack.getName().getString();
        boolean stacked = stack.getCount() > 1;
        BlockPos position = item.getBlockPos();
        alerts.feed(HuntFeed.Type.ITEM, stacked ? name + " ×" + stack.getCount() : name, position);
        String shown = stacked ? name + " x" + stack.getCount() : name;
        alerts.chat("§d" + shown + " §7found on ground at §f" + coordinates(position));
        alerts.ding();
        alerts.discord(discord.items, "Ground Item Alert", "**" + shown + "** found on ground\nCoords: "
            + coordinates(position) + "\nDimension: " + DimensionNames.current());
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + " " + position.getY() + " " + position.getZ();
    }
}
