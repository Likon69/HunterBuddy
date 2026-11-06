package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.render.SkyboxStyle;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class Skybox extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgWorld = settings.createGroup("World");

    private final Setting<SkyboxStyle> style = sgGeneral.add(new EnumSetting.Builder<SkyboxStyle>()
        .name("style")
        .description("The animated sky that replaces the Overworld sky.")
        .defaultValue(SkyboxStyle.AURORA)
        .build());

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Animation speed of the sky.")
        .defaultValue(1.0).min(0.1).max(4.0).sliderRange(0.1, 4.0)
        .build());

    private final Setting<Double> intensity = sgGeneral.add(new DoubleSetting.Builder()
        .name("intensity")
        .description("Brightness of the sky.")
        .defaultValue(1.0).min(0.2).max(2.0).sliderRange(0.2, 2.0)
        .build());

    private final Setting<Double> starDensity = sgGeneral.add(new DoubleSetting.Builder()
        .name("star-density")
        .description("How many stars the styles that show stars draw.")
        .defaultValue(0.5).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .build());

    private final Setting<Boolean> keepCelestial = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-celestial")
        .description("Keeps the vanilla sun, moon and stars on top of the shader sky.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> keepClouds = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-clouds")
        .description("Keeps the vanilla clouds.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> endSky = sgGeneral.add(new BoolSetting.Builder()
        .name("end-sky")
        .description("Also replaces the End sky.")
        .defaultValue(true)
        .build());

    private final Setting<SkyboxStyle> endStyle = sgGeneral.add(new EnumSetting.Builder<SkyboxStyle>()
        .name("end-style")
        .description("The animated sky that replaces the End sky.")
        .defaultValue(SkyboxStyle.NEBULA)
        .visible(endSky::get)
        .build());

    private final Setting<SettingColor> auroraGreen = color("aurora-green", "Lower colour of the aurora curtains.",
        new SettingColor(60, 255, 140), SkyboxStyle.AURORA);
    private final Setting<SettingColor> auroraViolet = color("aurora-violet", "Upper colour of the aurora curtains.",
        new SettingColor(150, 80, 255), SkyboxStyle.AURORA);

    private final Setting<SettingColor> nebulaMagenta = color("nebula-magenta", "First gas colour of the nebula.",
        new SettingColor(255, 60, 180), SkyboxStyle.NEBULA);
    private final Setting<SettingColor> nebulaCyan = color("nebula-cyan", "Second gas colour of the nebula.",
        new SettingColor(40, 160, 255), SkyboxStyle.NEBULA);
    private final Setting<SettingColor> nebulaGold = color("nebula-gold", "Highlight colour of the nebula.",
        new SettingColor(255, 190, 80), SkyboxStyle.NEBULA);

    private final Setting<SettingColor> twilightHorizon = color("twilight-horizon", "Colour of the sky at the horizon.",
        new SettingColor(255, 120, 50), SkyboxStyle.TWILIGHT);
    private final Setting<SettingColor> twilightZenith = color("twilight-zenith", "Colour of the sky overhead.",
        new SettingColor(60, 40, 120), SkyboxStyle.TWILIGHT);
    private final Setting<SettingColor> twilightClouds = color("twilight-clouds", "Colour the clouds are lit with.",
        new SettingColor(255, 150, 170), SkyboxStyle.TWILIGHT);

    private final Setting<SettingColor> stormCloud = color("storm-cloud", "Colour of the storm clouds.",
        new SettingColor(70, 80, 110), SkyboxStyle.STORM_NIGHT);
    private final Setting<SettingColor> stormFlash = color("storm-flash", "Colour of the lightning.",
        new SettingColor(200, 210, 255), SkyboxStyle.STORM_NIGHT);

    private final Setting<SettingColor> voidTop = color("void-top", "Colour of the sky overhead.",
        new SettingColor(10, 10, 40), SkyboxStyle.DEEP_VOID);
    private final Setting<SettingColor> voidBottom = color("void-bottom", "Colour of the sky at the horizon.",
        new SettingColor(60, 20, 90), SkyboxStyle.DEEP_VOID);

    private final Setting<Boolean> followWorldTime = sgWorld.add(new BoolSetting.Builder()
        .name("follow-world-time")
        .description("Twilight follows the real sun, and Aurora and Storm Night only show at night.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> rainDims = sgWorld.add(new BoolSetting.Builder()
        .name("rain-dims")
        .description("Dims the sky while it rains.")
        .defaultValue(true)
        .build());

    private final Setting<Double> horizonFogBlend = sgWorld.add(new DoubleSetting.Builder()
        .name("horizon-fog-blend")
        .description("How strongly the sky fades into the fog colour at and below the horizon.")
        .defaultValue(0.6).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .build());

    public Skybox() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "skybox", "Replaces the sky with an animated shader sky.");
    }

    private Setting<SettingColor> color(String name, String description, SettingColor defaultValue, SkyboxStyle owner) {
        return sgColors.add(new ColorSetting.Builder()
            .name(name)
            .description(description)
            .defaultValue(defaultValue)
            .visible(() -> uses(owner))
            .build());
    }

    private boolean uses(SkyboxStyle candidate) {
        return style.get() == candidate || (endSky.get() && endStyle.get() == candidate);
    }

    public SkyboxStyle style(boolean end) {
        return end ? endStyle.get() : style.get();
    }

    public SettingColor[] palette(SkyboxStyle forStyle) {
        return switch (forStyle) {
            case AURORA -> new SettingColor[] {auroraGreen.get(), auroraViolet.get(), auroraViolet.get()};
            case NEBULA -> new SettingColor[] {nebulaMagenta.get(), nebulaCyan.get(), nebulaGold.get()};
            case TWILIGHT -> new SettingColor[] {twilightHorizon.get(), twilightZenith.get(), twilightClouds.get()};
            case STORM_NIGHT -> new SettingColor[] {stormCloud.get(), stormFlash.get(), stormFlash.get()};
            case DEEP_VOID -> new SettingColor[] {voidTop.get(), voidBottom.get(), voidBottom.get()};
        };
    }

    public double speed() {
        return speed.get();
    }

    public double intensity() {
        return intensity.get();
    }

    public double starDensity() {
        return starDensity.get();
    }

    public boolean keepCelestial() {
        return keepCelestial.get();
    }

    public boolean keepClouds() {
        return keepClouds.get();
    }

    public boolean endSky() {
        return endSky.get();
    }

    public boolean followWorldTime() {
        return followWorldTime.get();
    }

    public boolean rainDims() {
        return rainDims.get();
    }

    public double horizonFogBlend() {
        return horizonFogBlend.get();
    }
}
