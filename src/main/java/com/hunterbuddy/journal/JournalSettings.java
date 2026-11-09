package com.hunterbuddy.journal;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class JournalSettings {
    public final JournalStore store = new JournalStore(2048);
    private final SettingGroup group;
    public final Setting<Boolean> enabled;
    public final Setting<Boolean> hideCoordinates;
    public final Setting<Boolean> profilePerformance;
    public final Setting<Boolean> autoOpen;
    public final Setting<Double> scale;
    public final Setting<Double> textScale;
    private final Setting<Integer> capacity;
    public final Setting<SettingColor> background;
    public final Setting<SettingColor> accent;
    public final Setting<SettingColor> meteor;
    public final Setting<SettingColor> baritone;
    public final Setting<SettingColor> hunterbuddy;
    public final Setting<SettingColor> other;
    public final Setting<SettingColor> warning;
    public final Setting<SettingColor> error;

    public JournalSettings(SettingGroup group, Runnable changed) {
        this.group = group;
        enabled = group.add(new BoolSetting.Builder().name("log")
            .description("Show the Log entry and capture local mod messages.").defaultValue(true).onChanged(value -> changed.run()).build());
        profilePerformance = group.add(new BoolSetting.Builder().name("profile-performance")
            .description("Record chat frame intervals and journal CPU submission stages for a performance report.").defaultValue(false).build());
        autoOpen = group.add(new BoolSetting.Builder().name("auto-open")
            .description("Open the journal when a new chat screen opens.").defaultValue(false).build());
        scale = group.add(new DoubleSetting.Builder().name("window-scale")
            .description("Window dimensions and spacing; text has its own scale.").defaultValue(1).min(0.6).max(1.8).sliderRange(0.7, 1.5).build());
        textScale = group.add(new DoubleSetting.Builder().name("text-scale")
            .description("Text size, measured independently from the window scale.").defaultValue(0.85).min(0.5).max(1.6).sliderRange(0.6, 1.4).build());
        capacity = group.add(new IntSetting.Builder().name("max-entries")
            .description("Maximum retained messages, each limited to 8192 characters.").defaultValue(2048).min(256).max(8192).sliderRange(512, 4096)
            .onChanged(store::resize).build());
        background = group.add(new ColorSetting.Builder().name("window-background")
            .description("Glass background and opacity.").defaultValue(new SettingColor(17, 24, 35, 195)).build());
        accent = color("log-accent", 106, 231, 193);
        meteor = color("meteor", 177, 153, 245);
        baritone = color("baritone", 112, 191, 247);
        hunterbuddy = color("hunterbuddy", 106, 231, 193);
        other = color("other-mods", 211, 221, 238);
        warning = color("warnings", 249, 202, 110);
        error = color("errors", 249, 126, 151);
        hideCoordinates = group.add(new BoolSetting.Builder().name("hide-coordinates")
            .description("Hide coordinates in the journal and copied messages.").defaultValue(true).build());
    }

    public void prepare() { store.resize(capacity.get()); }

    public SettingColor color(JournalEntry entry) {
        if (entry.level() == JournalEntry.Level.ERROR) return error.get();
        if (entry.level() == JournalEntry.Level.WARN) return warning.get();
        return switch (entry.source()) {
            case METEOR -> meteor.get();
            case BARITONE -> baritone.get();
            case HUNTERBUDDY -> hunterbuddy.get();
            case OTHER -> other.get();
        };
    }

    private Setting<SettingColor> color(String name, int r, int g, int b) {
        return group.add(new ColorSetting.Builder().name(name).description("Journal color and opacity.")
            .defaultValue(new SettingColor(r, g, b, 255)).build());
    }
}
