package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.journal.JournalCapture;
import com.hunterbuddy.journal.JournalEntry;
import com.hunterbuddy.journal.JournalOverlay;
import com.hunterbuddy.journal.JournalStore;
import meteordevelopment.meteorclient.events.meteor.CustomFontChangedEvent;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class ClientLog extends Module {
    public final JournalStore store = new JournalStore(2048);
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup colors = settings.createGroup("Colors");
    public final Setting<Double> scale = general.add(new DoubleSetting.Builder().name("scale")
        .description("Window dimensions and spacing; text has its own scale.").defaultValue(1).min(0.6).max(1.8).sliderRange(0.7, 1.5).build());
    public final Setting<Double> textScale = general.add(new DoubleSetting.Builder().name("text-scale")
        .description("Text size, measured independently from the window scale.").defaultValue(0.85).min(0.5).max(1.6).sliderRange(0.6, 1.4).build());
    private final Setting<Integer> capacity = general.add(new IntSetting.Builder().name("max-entries")
        .description("Maximum retained messages, each limited to 8192 characters.").defaultValue(2048).min(256).max(8192).sliderRange(512, 4096)
        .onChanged(store::resize).build());
    public final Setting<SettingColor> background = colors.add(new ColorSetting.Builder().name("background")
        .description("Glass background and opacity.").defaultValue(new SettingColor(17, 24, 35, 195)).build());
    public final Setting<SettingColor> accent = color("accent", 106, 231, 193);
    public final Setting<SettingColor> meteor = color("meteor", 177, 153, 245);
    public final Setting<SettingColor> baritone = color("baritone", 112, 191, 247);
    public final Setting<SettingColor> hunterbuddy = color("hunterbuddy", 106, 231, 193);
    public final Setting<SettingColor> other = color("other-mods", 211, 221, 238);
    public final Setting<SettingColor> warning = color("warnings", 249, 202, 110);
    public final Setting<SettingColor> error = color("errors", 249, 126, 151);
    private final JournalOverlay overlay = new JournalOverlay(this);

    public ClientLog() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "log", "Glass journal of local mod messages, opened from the chat. Server chat is excluded.");
        runInMainMenu = true;
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            JournalCapture.stop();
            overlay.shutdown();
        });
    }

    @Override
    public void onActivate() {
        store.resize(capacity.get());
        JournalCapture.start(store);
        JournalOverlay.activate(overlay);
    }

    @Override
    public void onDeactivate() {
        JournalCapture.stop();
        JournalOverlay.deactivate(overlay);
    }

    @EventHandler
    private void onFontChanged(CustomFontChangedEvent event) {
        overlay.fontChanged();
    }

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
        return colors.add(new ColorSetting.Builder().name(name).description("Journal color and opacity.")
            .defaultValue(new SettingColor(r, g, b, 255)).build());
    }
}
