package com.hunterbuddy.modules.regear.arealoader;

import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

final class AreaZigZagSettings {
    final Setting<Integer> legLength;
    final Setting<Integer> rowGap;

    AreaZigZagSettings(SettingGroup group, AreaModeSettings modeSettings) {
        legLength = group.add(new IntSetting.Builder()
            .name("zigzag-leg-length")
            .description("Length of every leg, in blocks.")
            .defaultValue(5000)
            .min(100)
            .sliderRange(500, 20000)
            .visible(modeSettings.shownIn(AreaLoaderModes.ZigZag))
            .build());
        rowGap = group.add(new IntSetting.Builder()
            .name("zigzag-row-gap")
            .description("Blocks sideways between two legs.")
            .defaultValue(192)
            .min(16)
            .sliderRange(16, 512)
            .visible(modeSettings.shownIn(AreaLoaderModes.ZigZag))
            .build());
    }
}
