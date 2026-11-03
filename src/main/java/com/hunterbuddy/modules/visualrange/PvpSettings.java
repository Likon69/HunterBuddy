package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

public final class PvpSettings {
    public final Setting<Boolean> crystalPlace;
    public final Setting<Boolean> crystalPop;
    public final Setting<Boolean> anchorPlace;
    public final Setting<Boolean> anchorPop;
    public final Setting<Integer> pvpRange;
    public final Setting<Boolean> ignoreSelf;

    public PvpSettings(Settings settings) {
        SettingGroup group = settings.createGroup("PvP Activity");
        crystalPlace = group.add(flag("crystal-place", "Alerts when an end crystal is placed."));
        crystalPop = group.add(flag("crystal-pop", "Alerts when an end crystal is destroyed."));
        anchorPlace = group.add(flag("anchor-place", "Alerts when a respawn anchor is placed."));
        anchorPop = group.add(flag("anchor-pop", "Alerts when a respawn anchor is destroyed."));
        pvpRange = group.add(new IntSetting.Builder()
            .name("pvp-range")
            .description("Maximum distance to attribute an activity to a player.")
            .defaultValue(6)
            .min(1)
            .sliderRange(1, 20)
            .build());
        ignoreSelf = group.add(flag("pvp-ignore-self", "Ignores your own activity."));
    }

    private static BoolSetting flag(String name, String description) {
        return new BoolSetting.Builder().name(name).description(description).defaultValue(true).build();
    }
}
