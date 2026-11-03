package com.hunterbuddy.modules.caveair;

import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class CaveAirOptions {
    private static final int WORLD_MIN_Y = -64;
    private static final int WORLD_MAX_Y = 320;

    Setting<Boolean> overworld;
    Setting<Boolean> nether;
    Setting<Boolean> end;
    Setting<Integer> minY;
    Setting<Integer> maxY;
    Setting<Integer> renderDistance;
    Setting<Boolean> debug;
    Setting<Boolean> portalSound;
    Setting<Boolean> highlightAllAir;
    Setting<Boolean> strictPortalShape;
    Setting<Integer> minCaveAirNeighbors;
    Setting<Integer> maxPortalWidth;
    Setting<Integer> maxPortalHeight;
    Setting<Boolean> showAllCaveAir;
    Setting<Integer> caveAirRenderDistance;
    Setting<SettingColor> caveAirFill;
    Setting<SettingColor> caveAirLine;
    Setting<ShapeMode> caveAirShape;
    Setting<SettingColor> disturbanceColor;
    Setting<SettingColor> lineColor;
    Setting<ShapeMode> shapeMode;
    Setting<Boolean> beacon;
    Setting<Integer> beaconHeight;

    public CaveAirOptions(Settings settings) {
        SettingGroup general = settings.getDefaultGroup();
        registerGeneral(general);
        registerDetection(settings.createGroup("Detection"));
        registerCaveAir(settings.createGroup("Cave Air Render"));
        registerRender(settings.createGroup("Render"));
        portalSound = flag(general, "portal-detection-sound", "Play a sound when a portal shape is found.", true);
    }

    private void registerGeneral(SettingGroup group) {
        overworld = flag(group, "overworld", "Scan the Overworld.", true);
        nether = flag(group, "nether", "Scan the Nether.", true);
        end = flag(group, "end", "Scan the End.", false);
        minY = group.add(number("min-y", "Lowest Y level to scan.", -60).sliderRange(WORLD_MIN_Y, WORLD_MAX_Y).build());
        maxY = group.add(number("max-y", "Highest Y level to scan.", 128).sliderRange(WORLD_MIN_Y, WORLD_MAX_Y).build());
        renderDistance = group.add(number("render-distance", "Maximum distance at which detections are drawn.", 128)
            .sliderRange(16, 256).build());
        debug = flag(group, "debug", "Print detection details in chat.", false);
    }

    private void registerDetection(SettingGroup group) {
        highlightAllAir = flag(group, "highlight-all-air-in-cave", "Also highlight every air block enclosed by cave air.", false);
        strictPortalShape = flag(group, "strict-portal-shape", "Only accept full rectangles, not rectangles missing their corners.", false);
        minCaveAirNeighbors = group.add(number("min-cave-air-neighbors", "Cave air blocks an air block must touch to count.", 1)
            .min(1).max(6).sliderRange(1, 6).build());
        maxPortalWidth = group.add(number("max-portal-width", "Widest portal shape accepted.", 8).sliderRange(4, 23).build());
        maxPortalHeight = group.add(number("max-portal-height", "Tallest portal shape accepted.", 10).sliderRange(5, 23).build());
    }

    private void registerCaveAir(SettingGroup group) {
        showAllCaveAir = flag(group, "show-all-cave-air", "Draw all cave air as merged boxes.", false);
        caveAirRenderDistance = group.add(number("cave-air-render-distance", "Maximum distance at which cave air is drawn.", 64)
            .sliderRange(16, 256).visible(showAllCaveAir::get).build());
        caveAirFill = group.add(color("cave-air-fill", "Fill color of cave air boxes.", new SettingColor(50, 150, 255, 30))
            .visible(showAllCaveAir::get).build());
        caveAirLine = group.add(color("cave-air-line", "Outline color of cave air boxes.", new SettingColor(50, 150, 255, 80))
            .visible(showAllCaveAir::get).build());
        caveAirShape = group.add(new EnumSetting.Builder<ShapeMode>().name("cave-air-shape")
            .description("How cave air boxes are drawn.").defaultValue(ShapeMode.Both).visible(showAllCaveAir::get).build());
    }

    private void registerRender(SettingGroup group) {
        disturbanceColor = group.add(color("disturbance-color", "Fill color of detections.", new SettingColor(255, 50, 50, 150)).build());
        lineColor = group.add(color("line-color", "Outline color of detections.", new SettingColor(255, 100, 100, 255)).build());
        shapeMode = group.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode")
            .description("How detections are drawn.").defaultValue(ShapeMode.Both).build());
        beacon = flag(group, "beacon", "Draw a vertical line above each portal shape.", true);
        beaconHeight = group.add(number("beacon-height", "Height of the beacon line.", 64).sliderRange(8, 128)
            .visible(beacon::get).build());
    }

    private static Setting<Boolean> flag(SettingGroup group, String name, String description, boolean value) {
        return group.add(new BoolSetting.Builder().name(name).description(description).defaultValue(value).build());
    }

    private static IntSetting.Builder number(String name, String description, int value) {
        return new IntSetting.Builder().name(name).description(description).defaultValue(value);
    }

    private static ColorSetting.Builder color(String name, String description, SettingColor value) {
        return new ColorSetting.Builder().name(name).description(description).defaultValue(value);
    }
}
