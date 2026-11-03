package com.hunterbuddy.modules.stash;

import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

import java.util.EnumMap;
import java.util.Map;

public final class StashThresholdSettings {
    private static final int STACKED_MINIMUM = 2;
    private static final int STACKED_SLIDER_MAX = 12;
    private static final int SLIDER_MAX = 20;

    private final Map<StashType, Setting<Integer>> minimums = new EnumMap<>(StashType.class);

    public StashThresholdSettings(SettingGroup group) {
        for (StashType type : StashType.values()) {
            minimums.put(type, group.add(builderFor(type).build()));
        }
    }

    public int minimum(StashType type) {
        return minimums.get(type).get();
    }

    private static IntSetting.Builder builderFor(StashType type) {
        IntSetting.Builder builder = new IntSetting.Builder()
            .name(type.minimumName())
            .description("Minimum " + type.label().toLowerCase() + " in a chunk to report it. 0 never triggers.")
            .defaultValue(type.minimumDefault());
        if (type == StashType.STACKED_MINECART) {
            return builder.min(STACKED_MINIMUM).sliderRange(STACKED_MINIMUM, STACKED_SLIDER_MAX);
        }
        return builder.min(0).sliderRange(0, SLIDER_MAX);
    }
}
