package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.hud.glass.GlassPanel;
import com.hunterbuddy.hud.system.SystemStatsPainter;
import com.hunterbuddy.hud.system.SystemStatsSampler;
import com.hunterbuddy.util.HudGlowPanel;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class SystemStatsHud extends HudElement {
    public static final HudElementInfo<SystemStatsHud> INFO = new HudElementInfo<>(HunterBuddyAddon.HUD_GROUP,
        "SystemStats", "Heap memory and process CPU usage with compact bars and sparklines.", SystemStatsHud::new);
    private final SettingGroup general = settings.getDefaultGroup();
    private final SettingGroup appearance = settings.createGroup("Panel");
    private final SettingGroup thresholds = settings.createGroup("Thresholds");
    private final SettingGroup colors = settings.createGroup("Colors");
    private final GlassPanel panel = new GlassPanel(general, appearance, settings.createGroup("Glow"));
    private final Setting<Boolean> showRam = general.add(new BoolSetting.Builder()
        .name("show-ram").description("Show used and maximum JVM heap memory.").defaultValue(true).build());
    private final Setting<Boolean> showCpu = general.add(new BoolSetting.Builder()
        .name("show-cpu").description("Show CPU usage of the game process.").defaultValue(true).build());
    private final Setting<Boolean> showGraph = general.add(new BoolSetting.Builder()
        .name("show-graph").description("Show a mini graph below each usage bar.").defaultValue(true).build());
    private final Setting<Integer> graphLength = general.add(new IntSetting.Builder()
        .name("graph-length").description("Recent half-second samples shown in each graph.")
        .defaultValue(60).min(2).max(60).sliderRange(10, 60).visible(showGraph::get).build());
    private final Setting<Integer> ramWarn = thresholds.add(new IntSetting.Builder()
        .name("ram-warn").description("RAM warning percentage.")
        .defaultValue(70).min(0).max(100).sliderRange(0, 100).build());
    private final Setting<Integer> ramCritical = thresholds.add(new IntSetting.Builder()
        .name("ram-critical").description("RAM critical percentage.")
        .defaultValue(85).min(0).max(100).sliderRange(0, 100).build());
    private final Setting<Integer> cpuWarn = thresholds.add(new IntSetting.Builder()
        .name("cpu-warn").description("Process CPU warning percentage.")
        .defaultValue(50).min(0).max(100).sliderRange(0, 100).build());
    private final Setting<Integer> cpuCritical = thresholds.add(new IntSetting.Builder()
        .name("cpu-critical").description("Process CPU critical percentage.")
        .defaultValue(75).min(0).max(100).sliderRange(0, 100).build());
    private final Setting<SettingColor> textColor = colors.add(new ColorSetting.Builder()
        .name("text-color").description("Labels and readings.")
        .defaultValue(new SettingColor(236, 243, 252, 255)).build());
    private final Setting<SettingColor> ramColor = colors.add(new ColorSetting.Builder()
        .name("ram-color").description("RAM bar and graph below warning level.")
        .defaultValue(new SettingColor(126, 200, 255, 255)).build());
    private final Setting<SettingColor> cpuColor = colors.add(new ColorSetting.Builder()
        .name("cpu-color").description("CPU bar and graph below warning level.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());
    private final Setting<SettingColor> warnColor = colors.add(new ColorSetting.Builder()
        .name("warn-color").description("Bar and graph warning color.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());
    private final Setting<SettingColor> criticalColor = colors.add(new ColorSetting.Builder()
        .name("critical-color").description("Bar and graph critical color.")
        .defaultValue(new SettingColor(255, 80, 80, 255)).build());
    private final SystemStatsSampler sampler = new SystemStatsSampler();
    private final SystemStatsSampler preview = SystemStatsSampler.preview();
    private final SystemStatsPainter painter = new SystemStatsPainter();

    public SystemStatsHud() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        if ((showRam.get() || showCpu.get()) && MeteorClient.mc.player != null && MeteorClient.mc.world != null) {
            sampler.refresh(System.nanoTime());
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        int rows = (showRam.get() ? 1 : 0) + (showCpu.get() ? 1 : 0);
        boolean live = MeteorClient.mc.player != null && MeteorClient.mc.world != null;
        if (rows == 0 || !live && !HudEditing.active()) {
            setSize(0, 0);
            return;
        }
        SystemStatsSampler data = live ? sampler : preview;
        painter.measure(renderer, data.ramDigits(), panel.scale(), showGraph.get(), rows);
        double pad = panel.padding();
        double left = x + pad;
        double top = y + pad;
        setSize(painter.width() + pad * 2, painter.height() + pad * 2);
        panel.draw(renderer, left, top, painter.width(), painter.height(), HudGlowPanel.Severity.OK);
        painter.draw(renderer, data, left, top, showRam.get(), showCpu.get(), showGraph.get(), graphLength.get(),
            ramWarn.get(), ramCritical.get(), cpuWarn.get(), cpuCritical.get(), textColor.get(), ramColor.get(),
            cpuColor.get(), warnColor.get(), criticalColor.get());
    }

    @Override
    public void onFontChanged() {
        painter.invalidate();
    }
}
