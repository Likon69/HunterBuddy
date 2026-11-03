package com.hunterbuddy.modules.stash;

import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

public record StashGroups(
    SettingGroup general,
    SettingGroup detection,
    SettingGroup thresholds,
    SettingGroup privacy,
    SettingGroup waypoints,
    SettingGroup discord,
    SettingGroup render
) {
    public static StashGroups create(Settings settings) {
        return new StashGroups(
            settings.getDefaultGroup(),
            settings.createGroup("Detection"),
            settings.createGroup("Thresholds"),
            settings.createGroup("Privacy"),
            settings.createGroup("Waypoints"),
            settings.createGroup("Discord"),
            settings.createGroup("Render"));
    }
}
