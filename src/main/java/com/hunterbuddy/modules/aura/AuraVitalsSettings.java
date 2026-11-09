package com.hunterbuddy.modules.aura;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class AuraVitalsSettings {
    public final Setting<Boolean> bars;
    public final Setting<Boolean> damage;
    public final Setting<Boolean> target;
    public final Setting<Boolean> recent;
    public final Setting<Boolean> nearby;
    public final Setting<Double> size;
    public final Setting<Double> distance;
    public final Setting<Double> duration;
    public final Setting<Double> damageDuration;
    public final Setting<Integer> maximum;
    public final Setting<SettingColor> healthy;
    public final Setting<SettingColor> warning;
    public final Setting<SettingColor> low;
    public final Setting<SettingColor> normalDamage;
    public final Setting<SettingColor> criticalDamage;
    public final Setting<SettingColor> absorption;
    public final Setting<SettingColor> trail;
    public final Setting<SettingColor> background;

    public AuraVitalsSettings(Settings settings) {
        SettingGroup group = settings.createGroup("Combat Health");
        bars = group.add(new BoolSetting.Builder().name("health-bars").description("Show camera-facing red nameplates with gold frames.").defaultValue(true).build());
        damage = group.add(new BoolSetting.Builder().name("floating-damage").description("Show observed health loss, never estimated damage or expired absorption.").defaultValue(true).build());
        target = group.add(new BoolSetting.Builder().name("health-current-target").description("Show the current aura target.").defaultValue(true).build());
        recent = group.add(new BoolSetting.Builder().name("health-recently-hit").description("Keep recently damaged aura targets visible, then fade them out.").defaultValue(true).build());
        nearby = group.add(new BoolSetting.Builder().name("health-nearby-mobs").description("Also show nearby mobs, excluding players.").defaultValue(false).build());
        size = group.add(new DoubleSetting.Builder().name("health-size").description("Screen-space bar size; text keeps shared fixed sizes.").defaultValue(1).min(0.6).max(1.8).sliderRange(0.6, 1.8).build());
        distance = group.add(new DoubleSetting.Builder().name("health-distance").description("Maximum overlay distance in blocks.").defaultValue(24).min(2).max(64).sliderRange(4, 48).build());
        duration = group.add(new DoubleSetting.Builder().name("health-duration").description("Seconds to retain a recently hit target, including its fade.").defaultValue(3).min(1).max(8).sliderRange(1, 8).build());
        damageDuration = group.add(new DoubleSetting.Builder().name("damage-duration").description("Seconds for a damage number to rise and fade.").defaultValue(1).min(0.4).max(2).sliderRange(0.4, 2).build());
        maximum = group.add(new IntSetting.Builder().name("health-max-entities").description("Maximum useful entities on screen; overlapping bars are suppressed.").defaultValue(8).min(1).max(16).sliderRange(1, 16).build());
        healthy = color(group, "health-healthy", 200, 24, 32, 255);
        warning = color(group, "health-warning", 200, 24, 32, 255);
        low = color(group, "health-low", 200, 24, 32, 255);
        normalDamage = color(group, "damage-color", 255, 255, 255, 255);
        criticalDamage = color(group, "critical-damage-color", 255, 173, 80, 255);
        absorption = color(group, "absorption-color", 255, 211, 90, 255);
        trail = color(group, "health-trail", 96, 12, 18, 255);
        background = color(group, "health-background", 0, 0, 0, 245);
    }

    public boolean enabled() { return bars.get() || damage.get(); }

    private static Setting<SettingColor> color(SettingGroup group, String name, int r, int g, int b, int a) {
        return group.add(new ColorSetting.Builder().name(name).description("Combat overlay color and opacity.").defaultValue(new SettingColor(r, g, b, a)).build());
    }
}
