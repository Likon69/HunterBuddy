package com.hunterbuddy.modules.recast;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

public final class RecastSettings {
    public final Setting<Boolean> disableIfNoRockets;
    public final Setting<Boolean> debugMessages;
    public final Setting<Integer> activationDelay;
    public final Setting<Integer> rocketDelay;
    public final Setting<Double> rotationSpeed;
    public final Setting<Double> targetPitch;
    public final Setting<Integer> fallDelay;
    public final Setting<Boolean> usePitch40Bounds;
    public final Setting<Integer> targetAltitude;
    public final Setting<Integer> minAltitude;

    public RecastSettings(Settings settings) {
        SettingGroup general = settings.getDefaultGroup();
        SettingGroup nether = settings.createGroup("Nether");
        SettingGroup overworld = settings.createGroup("Overworld/End");
        disableIfNoRockets = general.add(disableIfNoRocketsSetting());
        debugMessages = general.add(debugMessagesSetting());
        activationDelay = general.add(activationDelaySetting());
        rocketDelay = general.add(rocketDelaySetting());
        rotationSpeed = general.add(rotationSpeedSetting());
        targetPitch = general.add(targetPitchSetting());
        fallDelay = nether.add(fallDelaySetting());
        usePitch40Bounds = overworld.add(usePitch40BoundsSetting());
        targetAltitude = overworld.add(targetAltitudeSetting());
        minAltitude = overworld.add(minAltitudeSetting());
    }

    private static Setting<Boolean> disableIfNoRocketsSetting() {
        return new BoolSetting.Builder()
            .name("disable-if-no-rockets")
            .description("Automatically disables the module when no rockets are available.")
            .defaultValue(true)
            .build();
    }

    private static Setting<Boolean> debugMessagesSetting() {
        return new BoolSetting.Builder()
            .name("debug-messages")
            .description("Prints what the recovery is doing.")
            .defaultValue(false)
            .build();
    }

    private static Setting<Integer> activationDelaySetting() {
        return new IntSetting.Builder()
            .name("activation-delay")
            .description("Ticks between the jump and the elytra activation. Kept for compatibility.")
            .defaultValue(3)
            .range(1, 20)
            .sliderRange(1, 20)
            .build();
    }

    private static Setting<Integer> rocketDelaySetting() {
        return new IntSetting.Builder()
            .name("rocket-delay")
            .description("Ticks between rockets during the ascent.")
            .defaultValue(15)
            .range(5, 60)
            .sliderRange(5, 60)
            .build();
    }

    private static Setting<Double> rotationSpeedSetting() {
        return new DoubleSetting.Builder()
            .name("rotation-speed")
            .description("Degrees of pitch change per tick.")
            .defaultValue(3.0)
            .range(0.5, 15.0)
            .sliderRange(0.5, 15.0)
            .build();
    }

    private static Setting<Double> targetPitchSetting() {
        return new DoubleSetting.Builder()
            .name("target-pitch")
            .description("Pitch while ascending. Negative looks up.")
            .defaultValue(-70.0)
            .range(-89.0, 0.0)
            .sliderRange(-89.0, 0.0)
            .build();
    }

    private static Setting<Integer> fallDelaySetting() {
        return new IntSetting.Builder()
            .name("fall-delay")
            .description("Ticks without flying before the recovery starts.")
            .defaultValue(5)
            .range(1, 40)
            .sliderRange(1, 40)
            .build();
    }

    private static Setting<Boolean> usePitch40BoundsSetting() {
        return new BoolSetting.Builder()
            .name("use-pitch40-bounds")
            .description("Uses the lower bound of Pitch40 as the minimum altitude.")
            .defaultValue(true)
            .build();
    }

    private static Setting<Integer> targetAltitudeSetting() {
        return new IntSetting.Builder()
            .name("target-altitude")
            .description("Altitude the recovery climbs to.")
            .defaultValue(360)
            .sliderRange(50, 400)
            .build();
    }

    private Setting<Integer> minAltitudeSetting() {
        return new IntSetting.Builder()
            .name("min-altitude")
            .description("Altitude below which the recovery starts.")
            .defaultValue(310)
            .sliderRange(10, 400)
            .visible(() -> !usePitch40Bounds.get())
            .build();
    }
}
