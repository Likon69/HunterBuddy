package com.hunterbuddy.modules.rotationdetector;

import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class RotationDetectorOptions {
    private static final int WORLD_MIN_Y = -64;
    private static final int WORLD_MAX_Y = 320;

    Setting<Boolean> logs;
    Setting<Boolean> basalt;
    Setting<Boolean> purpurPillars;
    Setting<Boolean> hayBlocks;
    Setting<Boolean> deepslate;
    Setting<Integer> minY;
    Setting<Integer> maxY;
    Setting<Boolean> ignoreNearAir;
    Setting<Integer> airCheckDistance;
    Setting<ShapeMode> shapeMode;
    Setting<SettingColor> sideColor;
    Setting<SettingColor> lineColor;
    Setting<Integer> renderDistance;
    Setting<Integer> chunksPerTick;
    Setting<Integer> maxCacheSize;

    public RotationDetectorOptions(Settings settings) {
        registerBlockToggles(settings.getDefaultGroup());
        registerFilters(settings.createGroup("Filters"));
        registerRender(settings.createGroup("Render"));
        registerPerformance(settings.createGroup("Performance"));
    }

    private void registerBlockToggles(SettingGroup group) {
        logs = toggle(group, "logs", "Detect logs lying on their side.");
        basalt = toggle(group, "basalt", "Detect basalt and polished basalt lying on their side.");
        purpurPillars = toggle(group, "purpur-pillars", "Detect purpur pillars lying on their side.");
        hayBlocks = toggle(group, "hay-blocks", "Detect hay blocks lying on their side.");
        deepslate = toggle(group, "deepslate", "Detect deepslate lying on its side.");
    }

    private void registerFilters(SettingGroup group) {
        minY = group.add(number("min-y", "Lowest Y level to scan.", WORLD_MIN_Y).sliderRange(WORLD_MIN_Y, WORLD_MAX_Y).build());
        maxY = group.add(number("max-y", "Highest Y level to scan.", WORLD_MAX_Y).sliderRange(WORLD_MIN_Y, WORLD_MAX_Y).build());
        ignoreNearAir = group.add(new BoolSetting.Builder().name("ignore-near-air")
            .description("Skip blocks that have air somewhere above them.").defaultValue(true).build());
        airCheckDistance = group.add(number("air-check-distance", "How many blocks above a block are checked for air.", 5)
            .min(1).sliderRange(1, 20).visible(ignoreNearAir::get).build());
    }

    private void registerRender(SettingGroup group) {
        shapeMode = group.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode")
            .description("How the boxes are drawn.").defaultValue(ShapeMode.Both).build());
        sideColor = group.add(new ColorSetting.Builder().name("side-color").description("Color of the box sides.")
            .defaultValue(new SettingColor(255, 0, 0, 40)).build());
        lineColor = group.add(new ColorSetting.Builder().name("line-color").description("Color of the box outlines.")
            .defaultValue(new SettingColor(255, 0, 0, 200)).build());
        renderDistance = group.add(number("render-distance", "Maximum distance at which boxes are drawn.", 256)
            .min(16).sliderRange(16, 512).build());
    }

    private void registerPerformance(SettingGroup group) {
        chunksPerTick = group.add(number("chunks-per-tick", "How many chunks are scanned each tick.", 4)
            .min(1).sliderRange(1, 16).build());
        maxCacheSize = group.add(number("max-cache-size", "Maximum number of chunks kept in the cache.", 4000)
            .min(100).sliderRange(100, 10000).build());
    }

    private static Setting<Boolean> toggle(SettingGroup group, String name, String description) {
        return group.add(new BoolSetting.Builder().name(name).description(description).defaultValue(true).build());
    }

    private static IntSetting.Builder number(String name, String description, int value) {
        return new IntSetting.Builder().name(name).description(description).defaultValue(value);
    }
}
