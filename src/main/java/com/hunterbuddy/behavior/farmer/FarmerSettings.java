package com.hunterbuddy.behavior.farmer;

import java.util.List;
import java.util.Objects;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

public final class FarmerSettings {
    private Setting<List<Block>> blocks;
    private Setting<List<Item>> depositItems;
    private Setting<Integer> radius;
    private Setting<BlockPos> chest;
    private Setting<Integer> threshold;
    private Setting<BlockPos> startPoint;
    private Setting<Boolean> pauseOnPlayer;

    public void register(SettingGroup settings) {
        Objects.requireNonNull(settings);
        if (blocks != null) throw new IllegalStateException("FarmerBuddy settings already registered");
        blocks = settings.add(new BlockListSetting.Builder()
            .name("block-types").description("Block types FarmerBuddy is allowed to mine and deposit.").build());
        depositItems = settings.add(new ItemListSetting.Builder()
            .name("deposit-items").description("Items to deposit; empty deposits the chosen block types' own items.").build());
        radius = settings.add(new IntSetting.Builder()
            .name("radius").description("Scan radius around FarmerBuddy for the next block to mine.")
            .defaultValue(8).min(1).max(16).sliderRange(1, 16).build());
        chest = settings.add(new BlockPosSetting.Builder()
            .name("deposit-chest").description("Chest position where mined blocks are deposited.")
            .defaultValue(BlockPos.ORIGIN).build());
        threshold = settings.add(new IntSetting.Builder()
            .name("inventory-threshold").description("Matching block count that triggers a deposit run.")
            .defaultValue(32).min(1).max(64).sliderRange(1, 64).build());
        startPoint = settings.add(new BlockPosSetting.Builder()
            .name("start-point").description("Point FarmerBuddy returns to when it has nothing else to do.")
            .defaultValue(BlockPos.ORIGIN).build());
        pauseOnPlayer = settings.add(new BoolSetting.Builder()
            .name("pause-on-player").description("Pause FarmerBuddy while another player is visible.")
            .defaultValue(true).build());
    }

    public List<Block> blocks() {
        return blocks == null ? List.of() : blocks.get();
    }

    public List<Item> depositItems() {
        return depositItems == null ? List.of() : depositItems.get();
    }

    public int radius() {
        return radius == null ? 8 : radius.get();
    }

    public BlockPos chest() {
        return chest == null ? BlockPos.ORIGIN : chest.get();
    }

    public int threshold() {
        return threshold == null ? 32 : threshold.get();
    }

    public BlockPos startPoint() {
        return startPoint == null ? BlockPos.ORIGIN : startPoint.get();
    }

    public String refusal(BlockPos feet) {
        if (blocks().isEmpty()) return "FarmerBuddy needs at least one block type";
        if (chest().equals(BlockPos.ORIGIN) || startPoint().equals(BlockPos.ORIGIN)) return "Set FarmerBuddy deposit-chest and start-point first";
        if (!feet.isWithinDistance(chest(), 128) || !feet.isWithinDistance(startPoint(), 128)) {
            return "FarmerBuddy deposit-chest and start-point must be within 128 blocks";
        }
        return null;
    }

    public boolean pauseOnPlayer() {
        return pauseOnPlayer == null || pauseOnPlayer.get();
    }
}
