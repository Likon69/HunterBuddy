package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.routine.DeathPolicy;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
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

    public static Supplier<DeathPolicy> death(SettingGroup settings) {
        if (settings.get("death-policy") != null) throw new IllegalStateException("death settings already registered");
        Setting<DeathPolicy> policy = settings.add(new EnumSetting.Builder<DeathPolicy>()
            .name("death-policy").description("Stop the bot and report player death.").defaultValue(DeathPolicy.STOP).build());
        return policy::get;
    }
}
