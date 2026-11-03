package com.hunterbuddy.modules.regear.arealoader;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

final class AreaFlightSettings {
    final Setting<Boolean> pitch40DisableAutoBoundAdjust;
    final Setting<AreaLoader.OverworldFlightMode> overworldFlightMode;
    final Setting<Boolean> pitch40AutoFirework;
    final Setting<Integer> spiralCornerReachDistance;
    final Setting<Integer> spiralMaxOffTrackDistance;

    private final AreaModeSettings modeSettings;

    AreaFlightSettings(Settings settings, AreaModeSettings modeSettings) {
        this.modeSettings = modeSettings;
        SettingGroup group = settings.createGroup("Flight");
        pitch40DisableAutoBoundAdjust = group.add(disableAutoBoundAdjustSetting());
        overworldFlightMode = group.add(flightModeSetting());
        pitch40AutoFirework = group.add(autoFireworkSetting());
        spiralCornerReachDistance = group.add(cornerReachSetting());
        spiralMaxOffTrackDistance = group.add(maxOffTrackSetting());
    }

    private Setting<Boolean> disableAutoBoundAdjustSetting() {
        return new BoolSetting.Builder()
            .name("pitch40-disable-auto-bound-adjust")
            .description("Stops the Pitch40 bounds from being adjusted automatically.")
            .defaultValue(false)
            .visible(this::isPitch40)
            .build();
    }

    private static Setting<AreaLoader.OverworldFlightMode> flightModeSetting() {
        return new EnumSetting.Builder<AreaLoader.OverworldFlightMode>()
            .name("overworld-flight-mode")
            .description("How the Overworld flight is driven.")
            .defaultValue(AreaLoader.OverworldFlightMode.PITCH40)
            .build();
    }

    private Setting<Boolean> autoFireworkSetting() {
        return new BoolSetting.Builder()
            .name("pitch40-auto-firework")
            .description("Lets Pitch40 use fireworks by itself.")
            .defaultValue(true)
            .visible(this::isPitch40)
            .build();
    }

    private Setting<Integer> cornerReachSetting() {
        return new IntSetting.Builder()
            .name("corner-reach-distance")
            .description("Distance at which a spiral corner counts as reached.")
            .defaultValue(20)
            .min(1)
            .sliderRange(1, 100)
            .visible(modeSettings.shownIn(AreaLoaderModes.Spiral))
            .build();
    }

    private Setting<Integer> maxOffTrackSetting() {
        return new IntSetting.Builder()
            .name("max-off-track-distance")
            .description("Distance from the spiral path that triggers a correction.")
            .defaultValue(500)
            .min(20)
            .sliderRange(50, 2000)
            .visible(modeSettings.shownIn(AreaLoaderModes.Spiral))
            .build();
    }

    private boolean isPitch40() {
        return overworldFlightMode.get() == AreaLoader.OverworldFlightMode.PITCH40;
    }
}
