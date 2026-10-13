package com.hunterbuddy.behavior.resource;

import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.util.Rockets;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class ConsumablePolicy {
    public static final int OBSIDIAN_RESERVE = 25;
    public static final int ENDER_CHESTS_MIN = 2;
    private final WorldReader world;
    private final BaritoneBridge baritone;
    private final IntSupplier rocketsMin;

    public ConsumablePolicy(WorldReader world, BaritoneBridge baritone, IntSupplier rocketsMin) {
        this.world = Objects.requireNonNull(world);
        this.baritone = Objects.requireNonNull(baritone);
        this.rocketsMin = Objects.requireNonNull(rocketsMin);
    }

    public boolean mayConsume(Item item) {
        int reserve = item == Items.OBSIDIAN ? OBSIDIAN_RESERVE : item == Items.ENDER_CHEST ? ENDER_CHESTS_MIN
            : item == Items.FIREWORK_ROCKET ? Math.max(0, rocketsMin.getAsInt()) : 0;
        return world.count(item) > reserve;
    }

    public boolean placement(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem && mayConsume(stack.getItem());
    }

    public boolean rocket() { return world.countMatching(Rockets::isPlain) > Math.max(0, rocketsMin.getAsInt()); }

    public boolean pillar(ItemStack stack) {
        return placement(stack) && baritone.settings().acceptableThrowawayItems.value.contains(stack.getItem());
    }

    public List<Item> throwawayItems(List<Item> items) {
        return items.stream().filter(item -> item instanceof BlockItem && mayConsume(item)).toList();
    }
}
