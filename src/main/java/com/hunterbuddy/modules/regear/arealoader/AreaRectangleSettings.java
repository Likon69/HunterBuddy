package com.hunterbuddy.modules.regear.arealoader;

import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import net.minecraft.util.math.BlockPos;

final class AreaRectangleSettings {
    final Setting<BlockPos> startPos;
    final Setting<BlockPos> targetPos;
    final Setting<Integer> rowGap;

    AreaRectangleSettings(SettingGroup group, AreaModeSettings modeSettings) {
        startPos = group.add(new BlockPosSetting.Builder()
            .name("start-position")
            .description("Where the rectangle starts. The Y value is ignored.")
            .defaultValue(BlockPos.ORIGIN)
            .visible(modeSettings.shownWithCoordinatesIn(AreaLoaderModes.Rectangle))
            .build());
        targetPos = group.add(new BlockPosSetting.Builder()
            .name("end-position")
            .description("Where the rectangle ends. The Y value is ignored.")
            .defaultValue(BlockPos.ORIGIN)
            .visible(modeSettings.shownWithCoordinatesIn(AreaLoaderModes.Rectangle))
            .build());
        rowGap = group.add(new IntSetting.Builder()
            .name("path-gap")
            .description("Chunks between two paths.")
            .defaultValue(18)
            .min(1)
            .sliderRange(0, 100)
            .build());
    }
}
