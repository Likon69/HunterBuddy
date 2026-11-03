package com.hunterbuddy.modules.flowesp;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

final class SettingFactory {
    private SettingFactory() {
    }

    static BoolSetting.Builder flag(String name, String description, boolean value) {
        return new BoolSetting.Builder().name(name).description(description).defaultValue(value);
    }

    static IntSetting.Builder number(String name, String description, int value) {
        return new IntSetting.Builder().name(name).description(description).defaultValue(value);
    }

    static DoubleSetting.Builder decimal(String name, String description, double value) {
        return new DoubleSetting.Builder().name(name).description(description).defaultValue(value);
    }

    static ColorSetting.Builder color(String name, String description, SettingColor value) {
        return new ColorSetting.Builder().name(name).description(description).defaultValue(value);
    }
}
