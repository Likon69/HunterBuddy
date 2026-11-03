package com.hunterbuddy.modules.scaffold;

import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class ScaffoldRenderSettings {
    public final Setting<Boolean> enabled;
    public final Setting<ShapeMode> shapeMode;
    public final Setting<SettingColor> sideColor;
    public final Setting<SettingColor> lineColor;

    private final SettingGroup group;

    ScaffoldRenderSettings(SettingGroup group) {
        this.group = group;
        enabled = enabled();
        shapeMode = shapeMode();
        sideColor = sideColor();
        lineColor = lineColor();
    }

    private Setting<Boolean> enabled() {
        return group.add(new BoolSetting.Builder()
            .name("render")
            .description("Draws the placed blocks.")
            .defaultValue(true)
            .build());
    }

    private Setting<ShapeMode> shapeMode() {
        return group.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are drawn.")
            .defaultValue(ShapeMode.Both)
            .visible(enabled::get)
            .build());
    }

    private Setting<SettingColor> sideColor() {
        return group.add(new ColorSetting.Builder()
            .name("side-color")
            .description("Colour of the block sides.")
            .defaultValue(new SettingColor(197, 137, 232, 10))
            .visible(enabled::get)
            .build());
    }

    private Setting<SettingColor> lineColor() {
        return group.add(new ColorSetting.Builder()
            .name("line-color")
            .description("Colour of the block outlines.")
            .defaultValue(new SettingColor(0, 1, 255))
            .visible(enabled::get)
            .build());
    }
}
