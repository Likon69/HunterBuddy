package com.hunterbuddy.modules.flowesp;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

final class AnimationOptions {
    final Setting<Boolean> fadeLoad;
    final Setting<Double> fadeDuration;
    final Setting<Boolean> pulseTips;
    final Setting<Integer> tipFluidLevel;
    final Setting<Double> pulseSpeed;
    final Setting<Double> pulseAlpha;
    final Setting<Double> pulseSize;

    AnimationOptions(Settings settings) {
        SettingGroup group = settings.createGroup("Animation");
        fadeLoad = group.add(SettingFactory.flag("fade-load", "Fade columns in and out instead of popping.", true).build());
        fadeDuration = group.add(SettingFactory.decimal("fade-duration", "Seconds a fade takes.", 0.4).min(0).sliderRange(0, 2)
            .visible(fadeLoad::get).build());
        pulseTips = group.add(SettingFactory.flag("pulse-flow-tips", "Pulse the columns at the end of a flow.", true).build());
        tipFluidLevel = group.add(SettingFactory.number("tip-fluid-level", "Fluid level at or below which a column counts as a flow tip.", 2)
            .min(1).sliderRange(1, 4).visible(pulseTips::get).build());
        pulseSpeed = group.add(SettingFactory.decimal("pulse-speed", "Pulses per second.", 1.0).min(0.1).sliderRange(0.1, 4)
            .visible(pulseTips::get).build());
        pulseAlpha = group.add(SettingFactory.decimal("pulse-alpha", "How much the opacity pulses.", 0.55).min(0).sliderRange(0, 1)
            .visible(pulseTips::get).build());
        pulseSize = group.add(SettingFactory.decimal("pulse-size", "How much the box swells while pulsing.", 0.12).min(0).sliderRange(0, 0.5)
            .visible(pulseTips::get).build());
    }
}
