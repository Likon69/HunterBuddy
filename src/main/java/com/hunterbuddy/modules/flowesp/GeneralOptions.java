package com.hunterbuddy.modules.flowesp;

import com.hunterbuddy.modules.FlowESP.GradientSource;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

final class GeneralOptions {
    Setting<SettingColor> lavaColor;
    Setting<SettingColor> waterColor;
    Setting<Integer> renderDistance;
    Setting<Boolean> debug;
    Setting<Boolean> gradientMode;
    Setting<SettingColor> lavaFresh;
    Setting<SettingColor> lavaMature;
    Setting<SettingColor> waterFresh;
    Setting<SettingColor> waterMature;
    Setting<GradientSource> gradientSource;
    Setting<Integer> maxFlowLength;
    Setting<Integer> maxAgeSeconds;
    Setting<Double> alphaFade;
    Setting<Double> distanceFade;

    GeneralOptions(Settings settings) {
        SettingGroup group = settings.getDefaultGroup();
        registerBase(group);
        registerGradientColors(group);
        registerGradientShape(group);
    }

    private void registerBase(SettingGroup group) {
        lavaColor = group.add(SettingFactory.color("lava-color", "Base color of lava columns.", new SettingColor(0, 48, 255, 28)).build());
        waterColor = group.add(SettingFactory.color("water-color", "Base color of water columns.", new SettingColor(0, 150, 255, 32)).build());
        renderDistance = group.add(SettingFactory.number("render-distance", "Maximum distance at which columns are drawn.", 400)
            .sliderRange(32, 320).build());
        debug = group.add(SettingFactory.flag("debug", "Print scan details in chat.", false).build());
        gradientMode = group.add(SettingFactory.flag("gradient-mode", "Color columns with a gradient that shows how mature the flow is.", true).build());
    }

    private void registerGradientColors(SettingGroup group) {
        lavaFresh = group.add(SettingFactory.color("lava-fresh-color", "Lava color of a young flow.", new SettingColor(255, 120, 0, 140))
            .visible(gradientMode::get).build());
        lavaMature = group.add(SettingFactory.color("lava-mature-color", "Lava color of a mature flow.", new SettingColor(180, 20, 0, 140))
            .visible(gradientMode::get).build());
        waterFresh = group.add(SettingFactory.color("water-fresh-color", "Water color of a young flow.", new SettingColor(0, 180, 255, 120))
            .visible(gradientMode::get).build());
        waterMature = group.add(SettingFactory.color("water-mature-color", "Water color of a mature flow.", new SettingColor(0, 40, 150, 120))
            .visible(gradientMode::get).build());
    }

    private void registerGradientShape(SettingGroup group) {
        gradientSource = group.add(new EnumSetting.Builder<GradientSource>().name("gradient-source")
            .description("What decides how mature a column looks.").defaultValue(GradientSource.FlowLength)
            .visible(gradientMode::get).build());
        maxFlowLength = group.add(SettingFactory.number("max-flow-length", "Flow length that counts as fully mature.", 10)
            .min(1).sliderRange(1, 64).build());
        maxAgeSeconds = group.add(SettingFactory.number("max-age-seconds", "Chunk age in seconds that counts as fully mature.", 600)
            .min(5).sliderRange(30, 3600)
            .visible(() -> gradientMode.get() && gradientSource.get() != GradientSource.FlowLength).build());
        alphaFade = group.add(SettingFactory.decimal("alpha-fade", "How much mature columns fade out.", 0.7).min(0).max(1).sliderMax(1).build());
        distanceFade = group.add(SettingFactory.decimal("distance-fade", "How much distant columns fade out.", 0.3).min(0).max(1).sliderMax(1).build());
    }
}
