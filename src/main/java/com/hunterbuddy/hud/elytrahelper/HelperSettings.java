package com.hunterbuddy.hud.elytrahelper;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

public final class HelperSettings {
    public final Setting<Double> textScale;
    public final Setting<Boolean> textShadow;
    public final Setting<Boolean> compactMode;
    public final HelperSections sections;
    public final HelperColors colors;

    public HelperSettings(Settings settings) {
        SettingGroup general = settings.getDefaultGroup();
        textScale = general.add(new DoubleSetting.Builder()
            .name("text-scale").description("Scale of the text.")
            .defaultValue(1.0).min(0.5).sliderRange(0.5, 3.0).build());
        textShadow = general.add(flag("text-shadow", "Draws a shadow behind the text.", true));
        compactMode = general.add(flag("compact-mode", "Uses shorter labels.", false));
        sections = new HelperSections(settings.createGroup("Sections"));
        colors = new HelperColors(settings.createGroup("Colors"));
    }

    private static Setting<Boolean> flag(String name, String description, boolean defaultValue) {
        return new BoolSetting.Builder().name(name).description(description).defaultValue(defaultValue).build();
    }
}
