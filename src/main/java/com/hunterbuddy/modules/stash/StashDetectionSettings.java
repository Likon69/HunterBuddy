package com.hunterbuddy.modules.stash;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

import java.util.EnumMap;
import java.util.Map;

public final class StashDetectionSettings {
    private final Map<StashType, Setting<Boolean>> detects = new EnumMap<>(StashType.class);

    public StashDetectionSettings(SettingGroup group) {
        for (StashType type : StashType.values()) {
            detects.put(type, group.add(new BoolSetting.Builder()
                .name(type.detectName())
                .description("Detects " + type.label().toLowerCase() + ".")
                .defaultValue(type.detectDefault())
                .build()));
        }
    }

    public boolean detects(StashType type) {
        return detects.get(type).get();
    }
}
