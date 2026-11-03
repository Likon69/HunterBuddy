package com.hunterbuddy.modules.regear.arealoader;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

final class AreaDebugSettings {
    final Setting<Boolean> debugMode;
    final Setting<Boolean> showGoalWaypoint;

    AreaDebugSettings(Settings settings) {
        SettingGroup group = settings.createGroup("Debug");
        debugMode = group.add(new BoolSetting.Builder()
            .name("debug-messages")
            .description("Prints debugging messages.")
            .defaultValue(false)
            .build());
        showGoalWaypoint = group.add(new BoolSetting.Builder()
            .name("show-goal-waypoint")
            .description("Shows a temporary Xaero waypoint at the current goal.")
            .defaultValue(false)
            .build());
    }
}
