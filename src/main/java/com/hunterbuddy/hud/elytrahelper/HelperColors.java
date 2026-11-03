package com.hunterbuddy.hud.elytrahelper;

import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class HelperColors {
    public final Setting<SettingColor> title;
    public final Setting<SettingColor> label;
    public final Setting<SettingColor> value;
    public final Setting<SettingColor> good;
    public final Setting<SettingColor> warn;
    public final Setting<SettingColor> danger;
    public final Setting<SettingColor> section;

    public HelperColors(SettingGroup group) {
        title = group.add(color("title-color", "Color of the title.", 255, 170, 0));
        label = group.add(color("label-color", "Color of the labels.", 180, 180, 180));
        value = group.add(color("value-color", "Color of the values.", 255, 255, 255));
        good = group.add(color("good-color", "Color for good values.", 85, 255, 85));
        warn = group.add(color("warn-color", "Color for warnings.", 255, 255, 85));
        danger = group.add(color("danger-color", "Color for dangerous values.", 255, 85, 85));
        section = group.add(color("section-color", "Color of the section headers.", 255, 170, 0));
    }

    private static Setting<SettingColor> color(String name, String description, int red, int green, int blue) {
        return new ColorSetting.Builder()
            .name(name).description(description)
            .defaultValue(new SettingColor(red, green, blue, 255)).build();
    }
}
