package com.hunterbuddy.modules.regear.arealoader;

import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

final class AreaNetherSettings {
    final Setting<AreaLoader.NetherPathMode> netherPathMode;
    final Setting<Integer> netherWaypointDistance;
    final Setting<Integer> netherWaypointReachDistance;

    AreaNetherSettings(Settings settings) {
        SettingGroup group = settings.createGroup("Nether");
        netherPathMode = group.add(new EnumSetting.Builder<AreaLoader.NetherPathMode>()
            .name("nether-path-mode")
            .description("How the Nether path is flown.")
            .defaultValue(AreaLoader.NetherPathMode.BARITONE_ELYTRA)
            .build());
        netherWaypointDistance = group.add(new IntSetting.Builder()
            .name("nether-waypoint-distance")
            .description("Distance between the goals given to Baritone.")
            .defaultValue(100)
            .min(50)
            .sliderRange(50, 500)
            .visible(this::usesBaritone)
            .build());
        netherWaypointReachDistance = group.add(new IntSetting.Builder()
            .name("nether-reach-distance")
            .description("Distance at which a Nether goal counts as reached.")
            .defaultValue(30)
            .min(10)
            .sliderRange(10, 100)
            .visible(this::usesBaritone)
            .build());
    }

    private boolean usesBaritone() {
        return netherPathMode.get() == AreaLoader.NetherPathMode.BARITONE_ELYTRA;
    }
}
