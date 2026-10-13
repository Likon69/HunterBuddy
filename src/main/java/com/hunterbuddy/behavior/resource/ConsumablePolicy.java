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
    private String flightOwner;
    private IntSupplier flightReserve = () -> 0;

    public ConsumablePolicy(WorldReader world, BaritoneBridge baritone, IntSupplier rocketsMin) {
        this.world = Objects.requireNonNull(world);
        this.baritone = Objects.requireNonNull(baritone);
        this.rocketsMin = Objects.requireNonNull(rocketsMin);
    }

    public boolean mayConsume(Item item) {
        int reserve = item == Items.OBSIDIAN ? OBSIDIAN_RESERVE : item == Items.ENDER_CHEST ? ENDER_CHESTS_MIN
            : item == Items.FIREWORK_ROCKET ? rocketReserve() : 0;
        return world.count(item) > reserve;
    }

    public boolean placement(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem && mayConsume(stack.getItem());
    }

    public boolean rocket() { return world.countMatching(Rockets::isPlain) > rocketReserve(); }

    private int rocketReserve() {
        return Math.max(Math.max(0, rocketsMin.getAsInt()), Math.max(0, flightReserve.getAsInt()));
    }

    public int minimumRockets() {
        return Math.max(0, rocketsMin.getAsInt());
    }

    public void claimFlight(String owner, IntSupplier reserve) {
        if (flightOwner != null) throw new IllegalStateException("another flight already owns the fuel reserve");
        flightOwner = Objects.requireNonNull(owner);
        flightReserve = Objects.requireNonNull(reserve);
    }

    public void releaseFlight(String owner) {
        if (!Objects.equals(owner, flightOwner)) return;
        flightOwner = null;
        flightReserve = () -> 0;
    }

    public boolean pillar(ItemStack stack) {
        return placement(stack) && baritone.settings().acceptableThrowawayItems.value.contains(stack.getItem());
    }

    public List<Item> throwawayItems(List<Item> items) {
        return items.stream().filter(item -> item instanceof BlockItem && mayConsume(item)).toList();
    }
}
