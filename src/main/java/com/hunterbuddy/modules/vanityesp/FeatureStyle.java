package com.hunterbuddy.modules.vanityesp;

import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

final class FeatureStyle {
    enum TracerColorOrder {
        AFTER_FLAGS,
        BEFORE_FLAGS
    }

    record Defaults(SettingColor side, SettingColor line, SettingColor tracer, boolean sides, boolean lines, boolean tracers) {
    }

    private final SettingGroup group;
    private final Setting<Boolean> toggle;
    final Setting<SettingColor> sideColor;
    final Setting<SettingColor> lineColor;
    final Setting<SettingColor> tracerColor;
    final Setting<Boolean> renderSides;
    final Setting<Boolean> renderLines;
    final Setting<Boolean> tracers;

    FeatureStyle(SettingGroup group, Setting<Boolean> toggle, Defaults defaults, TracerColorOrder order) {
        this.group = group;
        this.toggle = toggle;
        sideColor = color("side-color", "Color of the box sides.", defaults.side());
        lineColor = color("line-color", "Color of the box outlines.", defaults.line());
        Setting<SettingColor> earlyTracer = order == TracerColorOrder.BEFORE_FLAGS
            ? color("tracer-color", "Color of the tracer.", defaults.tracer()) : null;
        renderSides = flag("render-sides", "Draw the box sides.", defaults.sides());
        renderLines = flag("render-lines", "Draw the box outlines.", defaults.lines());
        tracers = flag("tracers", "Draw a tracer line to each find.", defaults.tracers());
        tracerColor = earlyTracer != null ? earlyTracer : color("tracer-color", "Color of the tracer.", defaults.tracer());
    }

    private Setting<SettingColor> color(String name, String description, SettingColor value) {
        return group.add(new ColorSetting.Builder().name(name).description(description).defaultValue(value)
            .visible(toggle::get).build());
    }

    private Setting<Boolean> flag(String name, String description, boolean value) {
        return group.add(new BoolSetting.Builder().name(name).description(description).defaultValue(value)
            .visible(toggle::get).build());
    }

    boolean drawsShapes() {
        return renderSides.get() || renderLines.get();
    }

    ShapeMode shapeMode() {
        if (renderSides.get() && renderLines.get()) return ShapeMode.Both;
        return renderSides.get() ? ShapeMode.Sides : ShapeMode.Lines;
    }
}
