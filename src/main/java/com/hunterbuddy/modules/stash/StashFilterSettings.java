package com.hunterbuddy.modules.stash;

import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.List;

public final class StashFilterSettings {
    public final Setting<List<Block>> blacklistedSupportBlocks;
    public final Setting<Boolean> oldChunksOnly;
    public final Setting<Boolean> ignoreScatteredContainers;
    public final Setting<Integer> packRadius;
    public final Setting<Integer> minPackedContainers;

    public StashFilterSettings(SettingGroup group) {
        blacklistedSupportBlocks = group.add(blacklistSetting());
        oldChunksOnly = group.add(oldChunksOnlySetting());
        ignoreScatteredContainers = group.add(ignoreScatteredSetting());
        packRadius = group.add(packRadiusSetting());
        minPackedContainers = group.add(minPackedSetting());
    }

    private static Setting<List<Block>> blacklistSetting() {
        return new BlockListSetting.Builder()
            .name("blacklisted-support-blocks")
            .description("Block entities standing on or next to these blocks are ignored.")
            .defaultValue(defaultBlacklist())
            .build();
    }

    private static Setting<Boolean> oldChunksOnlySetting() {
        return new BoolSetting.Builder()
            .name("old-chunks-only")
            .description("Only reports chunks the XaeroPlus Old Chunks module calls old. Nothing is reported while it is off.")
            .defaultValue(true)
            .build();
    }

    private static Setting<Boolean> ignoreScatteredSetting() {
        return new BoolSetting.Builder()
            .name("ignore-scattered-containers")
            .description("Ignores chunks whose containers are spread out, like generated structures.")
            .defaultValue(true)
            .build();
    }

    private Setting<Integer> packRadiusSetting() {
        return new IntSetting.Builder()
            .name("pack-radius")
            .description("Radius in blocks within which containers count as packed.")
            .defaultValue(5)
            .min(1)
            .sliderRange(2, 16)
            .visible(ignoreScatteredContainers::get)
            .build();
    }

    private Setting<Integer> minPackedSetting() {
        return new IntSetting.Builder()
            .name("min-packed-containers")
            .description("Containers within the pack radius needed to count as a stash.")
            .defaultValue(5)
            .min(2)
            .sliderRange(2, 20)
            .visible(ignoreScatteredContainers::get)
            .build();
    }

    private static Block[] defaultBlacklist() {
        return new Block[] {
            Blocks.OXIDIZED_COPPER, Blocks.OXIDIZED_CUT_COPPER, Blocks.TUFF_BRICKS, Blocks.WAXED_COPPER_BLOCK,
            Blocks.WAXED_OXIDIZED_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.WAXED_COPPER_BULB, Blocks.BARREL,
            Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
            Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.SCULK,
            Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.SANDSTONE, Blocks.CUT_SANDSTONE,
            Blocks.CHISELED_SANDSTONE, Blocks.MOSSY_COBBLESTONE, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS,
            Blocks.DARK_PRISMARINE, Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR,
            Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE, Blocks.BLACKSTONE,
            Blocks.GILDED_BLACKSTONE, Blocks.NETHER_BRICKS, Blocks.DARK_OAK_PLANKS, Blocks.OAK_PLANKS,
            Blocks.DARK_OAK_LOG, Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.SPRUCE_PLANKS,
            Blocks.NETHERRACK, Blocks.CRYING_OBSIDIAN
        };
    }
}
