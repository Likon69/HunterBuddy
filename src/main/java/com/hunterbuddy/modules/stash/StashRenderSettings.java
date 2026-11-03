package com.hunterbuddy.modules.stash;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class StashRenderSettings {
    public final Setting<Boolean> renderTracer;
    public final Setting<SettingColor> tracerColor;
    public final Setting<Integer> hideAtDistance;
    public final Setting<Integer> maxTraceDistance;
    public final Setting<Boolean> renderColumn;
    public final Setting<SettingColor> columnColor;
    public final Setting<Keybind> clearTraces;

    public StashRenderSettings(SettingGroup group) {
        renderTracer = group.add(renderTracerSetting());
        tracerColor = group.add(tracerColorSetting());
        hideAtDistance = group.add(hideAtDistanceSetting());
        maxTraceDistance = group.add(maxTraceDistanceSetting());
        renderColumn = group.add(renderColumnSetting());
        columnColor = group.add(columnColorSetting());
        clearTraces = group.add(clearTracesSetting());
    }

    private static Setting<Boolean> renderTracerSetting() {
        return new BoolSetting.Builder()
            .name("render-tracer")
            .description("Draws a line to every found stash.")
            .defaultValue(true)
            .build();
    }

    private Setting<SettingColor> tracerColorSetting() {
        return new ColorSetting.Builder()
            .name("tracer-color")
            .description("Color of the tracer lines.")
            .defaultValue(new SettingColor(255, 215, 0, 255))
            .visible(renderTracer::get)
            .build();
    }

    private Setting<Integer> hideAtDistanceSetting() {
        return new IntSetting.Builder()
            .name("hide-at-distance")
            .description("Tracers closer than this many blocks are removed.")
            .defaultValue(16)
            .min(1)
            .sliderRange(1, 100)
            .visible(renderTracer::get)
            .build();
    }

    private Setting<Integer> maxTraceDistanceSetting() {
        return new IntSetting.Builder()
            .name("max-trace-distance")
            .description("Tracers farther than this many blocks are not drawn.")
            .defaultValue(5000)
            .min(100)
            .sliderRange(100, 30000)
            .visible(renderTracer::get)
            .build();
    }

    private static Setting<Boolean> renderColumnSetting() {
        return new BoolSetting.Builder()
            .name("render-column")
            .description("Outlines the chunk column of every found stash.")
            .defaultValue(false)
            .build();
    }

    private Setting<SettingColor> columnColorSetting() {
        return new ColorSetting.Builder()
            .name("column-color")
            .description("Color of the chunk column outline.")
            .defaultValue(new SettingColor(255, 215, 0, 100))
            .visible(renderColumn::get)
            .build();
    }

    private static Setting<Keybind> clearTracesSetting() {
        return new KeybindSetting.Builder()
            .name("clear-traces")
            .description("Clears all the tracers.")
            .defaultValue(Keybind.none())
            .build();
    }
}
