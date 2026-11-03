package com.hunterbuddy.hud.movement;

import com.hunterbuddy.hud.MovementStatusHud.DisplayMode;
import com.hunterbuddy.hud.MovementStatusHud.Layout;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class MovementStatusOptions {
    private static final double MIN_SCALE = 0.5;
    private static final double MAX_SCALE = 3.0;

    public Setting<DisplayMode> displayMode;
    public Setting<Boolean> showSneaking;
    public Setting<Boolean> showSprinting;
    public Setting<Layout> layout;
    public Setting<Double> iconScale;
    public Setting<String> sneakingText;
    public Setting<String> sprintingText;
    public Setting<Double> textScale;
    public Setting<Boolean> textShadow;
    public Setting<String> separator;
    public Setting<String> prefix;
    public Setting<String> suffix;
    public Setting<Integer> fadeDelay;
    public Setting<SettingColor> sneakingColor;
    public Setting<SettingColor> sprintingColor;

    public MovementStatusOptions(Settings settings) {
        registerGeneral(settings.getDefaultGroup());
        registerText(settings.createGroup("Text"));
        registerActionBar(settings.createGroup("Action Bar"));
        registerColors(settings.createGroup("Colors"));
    }

    private void registerGeneral(SettingGroup group) {
        displayMode = group.add(new EnumSetting.Builder<DisplayMode>().name("display-mode")
            .description("How the status is shown.").defaultValue(DisplayMode.Text).build());
        showSneaking = group.add(new BoolSetting.Builder().name("show-sneaking")
            .description("Show the sneaking status.").defaultValue(true).build());
        showSprinting = group.add(new BoolSetting.Builder().name("show-sprinting")
            .description("Show the sprinting status.").defaultValue(true).build());
        layout = group.add(new EnumSetting.Builder<Layout>().name("layout")
            .description("Whether the entries are stacked or placed side by side.").defaultValue(Layout.Vertical)
            .visible(() -> displayMode.get() != DisplayMode.ActionBar).build());
        iconScale = group.add(scale("icon-scale", "Size of the icons.", () -> displayMode.get() == DisplayMode.Icon));
    }

    private void registerText(SettingGroup group) {
        sneakingText = group.add(string("sneaking-text", "Text shown while sneaking.", "[Sneaking]", this::usesText));
        sprintingText = group.add(string("sprinting-text", "Text shown while sprinting.", "[Sprinting]", this::usesText));
        textScale = group.add(scale("text-scale", "Size of the text.", () -> displayMode.get() == DisplayMode.Text));
        textShadow = group.add(new BoolSetting.Builder().name("text-shadow").description("Draw a shadow behind the text.")
            .defaultValue(true).visible(() -> displayMode.get() == DisplayMode.Text).build());
    }

    private void registerActionBar(SettingGroup group) {
        separator = group.add(string("separator", "Text placed between the two statuses.", " ", this::usesActionBar));
        prefix = group.add(string("prefix", "Text placed before the statuses.", "", this::usesActionBar));
        suffix = group.add(string("suffix", "Text placed after the statuses.", "", this::usesActionBar));
        fadeDelay = group.add(new IntSetting.Builder().name("fade-delay")
            .description("Ticks the message stays after the status ends, 0 clears it at once.")
            .defaultValue(20).min(0).max(100).sliderRange(0, 100).visible(this::usesActionBar).build());
    }

    private void registerColors(SettingGroup group) {
        sneakingColor = group.add(new ColorSetting.Builder().name("sneaking-color").description("Color of the sneaking status.")
            .defaultValue(new SettingColor(100, 200, 255, 255)).build());
        sprintingColor = group.add(new ColorSetting.Builder().name("sprinting-color").description("Color of the sprinting status.")
            .defaultValue(new SettingColor(255, 200, 0, 255)).build());
    }

    private boolean usesText() {
        return displayMode.get() == DisplayMode.Text || displayMode.get() == DisplayMode.ActionBar;
    }

    private boolean usesActionBar() {
        return displayMode.get() == DisplayMode.ActionBar;
    }

    private static Setting<Double> scale(String name, String description, IVisible visible) {
        return new DoubleSetting.Builder().name(name).description(description).defaultValue(1.0)
            .min(MIN_SCALE).max(MAX_SCALE).sliderRange(MIN_SCALE, MAX_SCALE).visible(visible).build();
    }

    private static Setting<String> string(String name, String description, String value, IVisible visible) {
        return new StringSetting.Builder().name(name).description(description).defaultValue(value).visible(visible).build();
    }
}
