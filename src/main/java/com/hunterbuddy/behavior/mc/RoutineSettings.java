package com.hunterbuddy.behavior.mc;

import java.util.function.BooleanSupplier;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class RoutineSettings {
    private RoutineSettings() {
    }

    public static BooleanSupplier session(SettingGroup settings) {
        if (settings.get("pause-on-foreign-screen") != null) throw new IllegalStateException("session settings already registered");
        Setting<Boolean> pause = settings.add(new BoolSetting.Builder()
            .name("pause-on-foreign-screen").description("Pause while a player-owned screen is open; stop after 60 seconds.")
            .defaultValue(true).build());
        return pause::get;
    }
}
