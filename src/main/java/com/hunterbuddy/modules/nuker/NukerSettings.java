package com.hunterbuddy.modules.nuker;

import java.util.List;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import net.minecraft.block.Block;

public final class NukerSettings {
    public final Setting<Double> range;
    public final Setting<NukerRules.Shape> shape;
    public final Setting<Boolean> ignoreStandingBlock;
    public final Setting<NukerRules.Filter> filter;
    public final Setting<List<Block>> blocks;
    public final Setting<NukerRules.Order> order;
    public final Setting<Boolean> visibleOnly;
    public final Setting<Boolean> antiBreak;
    public final Setting<Integer> minimumDurability;
    public final Setting<Boolean> render;

    public NukerSettings(Settings settings) {
        SettingGroup general = settings.getDefaultGroup();
        SettingGroup rendering = settings.createGroup("Render");
        range = general.add(new DoubleSetting.Builder()
            .name("range")
            .description("Maximum distance from your eyes to a target block. Also limited by HMine's range.")
            .defaultValue(4.5)
            .range(0.1, 6.0)
            .sliderRange(0.1, 6.0)
            .build());
        shape = general.add(new EnumSetting.Builder<NukerRules.Shape>()
            .name("shape")
            .description("All mines around you; Flatten keeps the floor; Tunnel selects feet and head level.")
            .defaultValue(NukerRules.Shape.All)
            .build());
        ignoreStandingBlock = general.add(new BoolSetting.Builder()
            .name("ignore-standing-block")
            .description("Keep the blocks supporting your feet, including both blocks when standing on an edge.")
            .defaultValue(true)
            .build());
        filter = general.add(new EnumSetting.Builder<NukerRules.Filter>()
            .name("block-filter")
            .description("Mine all blocks, only listed blocks, or everything except listed blocks.")
            .defaultValue(NukerRules.Filter.All)
            .build());
        blocks = general.add(new BlockListSetting.Builder()
            .name("blocks")
            .description("Blocks used by the whitelist or blacklist.")
            .visible(() -> filter.get() != NukerRules.Filter.All)
            .build());
        order = general.add(new EnumSetting.Builder<NukerRules.Order>()
            .name("mine-order")
            .description("Choose the closest, farthest, fastest or slowest eligible block first.")
            .defaultValue(NukerRules.Order.Closest)
            .build());
        visibleOnly = general.add(new BoolSetting.Builder()
            .name("visible-only")
            .description("Require an unobstructed ray to a target's outline before mining it.")
            .defaultValue(true)
            .build());
        antiBreak = general.add(new BoolSetting.Builder()
            .name("anti-break")
            .description("Stop selecting a tool when mining could consume its durability reserve.")
            .defaultValue(true)
            .build());
        minimumDurability = general.add(new IntSetting.Builder()
            .name("anti-break-percent")
            .description("Durability to keep, with additional uses reserved for HMine's simultaneous targets.")
            .defaultValue(10)
            .range(0, 100)
            .sliderRange(0, 100)
            .visible(antiBreak::get)
            .build());
        render = rendering.add(new BoolSetting.Builder()
            .name("render")
            .description("Show HMine's progress bars and queue using its render style and colors.")
            .defaultValue(true)
            .build());
    }
}
