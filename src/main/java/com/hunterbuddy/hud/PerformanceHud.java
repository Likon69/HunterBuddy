package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.hud.glass.GlassStyle;
import com.hunterbuddy.hud.performance.PerformanceMetric;
import com.hunterbuddy.hud.performance.PerformancePainter;
import com.hunterbuddy.hud.toast.ModuleToastBlur;
import com.hunterbuddy.util.HudPulse;
import com.hunterbuddy.util.PingSampler;
import com.hunterbuddy.util.TpsSampler;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class PerformanceHud extends HudElement {
    public static final HudElementInfo<PerformanceHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "Performance",
        "Three glass capsules for FPS, ping and server TPS.",
        PerformanceHud::new);
    private static final float TPS_WARN = 14.0f;
    private static final float TPS_CRITICAL = 9.0f;
    private static final float TPS_FLATLINE_SECONDS = 1.0f;
    private static final float TPS_MAX = 20.0f;
    private static final long SPIKE_HOLD_MS = 2000L;
    private static final int PING_SPIKE_FLOOR_MS = 40;
    private static final float PING_SPIKE_JITTER_MULT = 2.0f;
    private static final int PING_BASELINE_MIN_SAMPLES = 5;

    private static final float TPS_DIP_FLOOR = 2.0f;
    private static final float TPS_DIP_SPREAD_MULT = 1.0f;
    private static final int TPS_BASELINE_MIN_SAMPLES = 6;
    private static final long FPS_SAMPLE_EVERY_MS = 1000L;
    private static final int FPS_HISTORY_CAPACITY = 10;
    private static final int FPS_BASELINE_MIN_SAMPLES = 4;
    private static final float FPS_MIN_BASELINE = 15.0f;
    private static final float FPS_DROP_FLOOR = 15.0f;
    private static final float FPS_DROP_FRACTION = 0.30f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgGlass = settings.createGroup("Glass");

    private final Setting<GlassStyle> style = sgGeneral.add(new EnumSetting.Builder<GlassStyle>()
        .name("style").description("Glass: rounded glass cards. Flat: square flat panel.")
        .defaultValue(GlassStyle.Glass).build());

    private final Setting<Double> scale = sgGlass.add(new DoubleSetting.Builder()
        .name("scale").description("Scale of the glass frame and spacing, independent of text size.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.6, 1.5).build());

    private final Setting<Boolean> backgroundBlur = sgGlass.add(new BoolSetting.Builder()
        .name("background-blur").description("Blur the actual scene behind the rounded glass frame.")
        .defaultValue(true).build());

    private final Setting<Integer> blurStrength = sgGlass.add(new IntSetting.Builder()
        .name("blur-strength").description("Background blur sampling radius.")
        .defaultValue(2).min(1).max(5).sliderRange(1, 5).visible(backgroundBlur::get).build());

    private final Setting<Boolean> glassReflection = sgGlass.add(new BoolSetting.Builder()
        .name("glass-reflection").description("Soft highlights reflected in the glass frame and sections.")
        .defaultValue(true).build());

    private final Setting<SettingColor> background = sgGlass.add(new ColorSetting.Builder()
        .name("background").description("Glass background color and opacity.")
        .defaultValue(new SettingColor(17, 24, 35, 184)).build());

    private final Setting<Boolean> compact = sgGlass.add(new BoolSetting.Builder()
        .name("compact").description("A thin single row of the three pills, without a title.")
        .defaultValue(false).build());

    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title").description("Display the title above the normal layout.")
        .defaultValue(false).visible(() -> !compact.get()).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Select normal or large fixed-size values; labels stay small.")
        .defaultValue(1.0).min(0.1).sliderRange(0.1, 3.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind the text.")
        .defaultValue(true).build());

    private final Setting<Integer> warnPing = sgGeneral.add(new IntSetting.Builder()
        .name("warn-ping").description("Milliseconds at or above which the ping turns warn.")
        .defaultValue(150).min(50).max(1000).sliderRange(80, 400).build());

    private final Setting<Integer> criticalPing = sgGeneral.add(new IntSetting.Builder()
        .name("critical-ping").description("Milliseconds at or above which the ping turns critical.")
        .defaultValue(400).min(100).max(2000).sliderRange(200, 800).build());

    private final Setting<Integer> warnFps = sgGeneral.add(new IntSetting.Builder()
        .name("warn-fps").description("Frames per second below which the FPS turns warn. Keep it under your fps cap.")
        .defaultValue(40).min(5).max(240).sliderRange(10, 144).build());

    private final Setting<Integer> criticalFps = sgGeneral.add(new IntSetting.Builder()
        .name("critical-fps").description("Frames per second below which the FPS turns critical.")
        .defaultValue(20).min(1).max(120).sliderRange(5, 60).build());

    private final Setting<SettingColor> titleColor = sgGeneral.add(new ColorSetting.Builder()
        .name("title-color").description("Color for the title text, and for a value with no data yet.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> fpsColor = sgGeneral.add(new ColorSetting.Builder()
        .name("fps-color").description("Color for the FPS value when it is healthy.")
        .defaultValue(new SettingColor(0, 255, 0, 255)).build());

    private final Setting<SettingColor> pingColor = sgGeneral.add(new ColorSetting.Builder()
        .name("ping-color").description("Color for the ping value when it is healthy.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());

    private final Setting<SettingColor> tpsColor = sgGeneral.add(new ColorSetting.Builder()
        .name("tps-color").description("Color for the TPS value when it is healthy.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());

    private final Setting<SettingColor> warnColor = sgGeneral.add(new ColorSetting.Builder()
        .name("warn-color").description("Color once a value crosses into its warn range.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final Setting<SettingColor> criticalColor = sgGeneral.add(new ColorSetting.Builder()
        .name("critical-color").description("Color once a value crosses into its critical range, and the flash for a spike or drop.")
        .defaultValue(new SettingColor(255, 80, 80, 255)).build());

    private final PerformancePainter painter = new PerformancePainter();
    private final PerformanceMetric[] metrics = {
        new PerformanceMetric("FPS", "8888", ""),
        new PerformanceMetric("Ping", "8888 ms", " ms"),
        new PerformanceMetric("TPS", "88.8", "")
    };
    private final int[] pingHistory = new int[PingSampler.CAPACITY];
    private final float[] tpsHistory = new float[TpsSampler.CAPACITY];
    private boolean pingSpiking;
    private boolean tpsDipping;
    private boolean fpsFalling;
    private boolean live;
    private int fps;
    private int ping = -1;
    private float tps = -1;
    private final float[] fpsHistory = new float[FPS_HISTORY_CAPACITY];
    private int fpsHead;
    private int fpsFilled;
    private long lastFpsSampleAt;
    private boolean wasLive;
    private long pingSpikeAt;
    private long tpsDipAt;
    private long fpsDropAt;

    public PerformanceHud() {
        super(INFO);
    }

    @Override
    public void tick(HudRenderer renderer) {
        long now = System.currentTimeMillis();
        live = MeteorClient.mc.player != null && MeteorClient.mc.getNetworkHandler() != null;
        if (live && !wasLive) {
            fpsHead = 0;
            fpsFilled = 0;
            lastFpsSampleAt = 0L;
        }
        wasLive = live;
        fps = MeteorClient.mc.getCurrentFps();
        sampleFps(fps, now);
        ping = live ? PingSampler.get().current() : -1;
        tps = live ? Math.min(TPS_MAX, TpsSampler.get().current()) : -1.0f;
        metrics[0].integer(fps);
        metrics[1].integer(ping);
        metrics[2].decimal(tps);
        int pingBefore = live ? PingSampler.get().copyHistory(pingHistory) - 1 : -1;
        pingSpiking = pingBefore >= PING_BASELINE_MIN_SAMPLES
            && pingHistory[pingBefore] - average(pingHistory, pingBefore)
                > Math.max(PING_SPIKE_FLOOR_MS, halfSpread(pingHistory, pingBefore) * PING_SPIKE_JITTER_MULT);
        int tpsCount = live ? TpsSampler.get().copyHistory(tpsHistory) : 0;
        for (int i = 0; i < tpsCount; i++) tpsHistory[i] = Math.min(TPS_MAX, tpsHistory[i]);
        int tpsBefore = tpsCount - 1;
        tpsDipping = tpsBefore >= TPS_BASELINE_MIN_SAMPLES
            && average(tpsHistory, tpsBefore) - tpsHistory[tpsBefore]
                > Math.max(TPS_DIP_FLOOR, spread(tpsHistory, tpsBefore) * TPS_DIP_SPREAD_MULT);
        float baseline = fpsBaseline();
        fpsFalling = fpsFilled >= FPS_BASELINE_MIN_SAMPLES && baseline >= FPS_MIN_BASELINE
            && baseline - fps > Math.max(FPS_DROP_FLOOR, baseline * FPS_DROP_FRACTION);
    }

    @Override
    public void render(HudRenderer renderer) {
        long now = System.currentTimeMillis();
        fpsDisplayColor(now);
        pingDisplayColor(now);
        tpsDisplayColor(live && TpsSampler.get().timeSinceLastTick() > TPS_FLATLINE_SECONDS, now);
        double s = scale.get();
        painter.style(style.get());
        painter.measure(renderer, metrics, s, textScale.get(), compact.get(), !compact.get() && showTitle.get(), textShadow.get());
        double pad = 8 * s;
        setSize(painter.width() + pad * 2, painter.height() + pad * 2);
        if (backgroundBlur.get()) ModuleToastBlur.INSTANCE.begin(blurStrength.get());
        painter.draw(renderer, metrics, x + pad, y + pad, now, backgroundBlur.get(), glassReflection.get(),
            background.get(), titleColor.get(), criticalColor.get());
    }

    @Override
    public void onFontChanged() {
        painter.invalidate();
    }

    private void pingDisplayColor(long now) {
        PerformanceMetric metric = metrics[1];
        metric.level = !live || ping < 0 ? 0 : ping >= criticalPing.get() ? 2 : ping >= warnPing.get() ? 1 : 0;
        SettingColor resting = !live || ping < 0 ? titleColor.get() : metric.level == 2 ? criticalColor.get()
            : metric.level == 1 ? warnColor.get() : pingColor.get();
        if (live && ping >= 0 && pingSpiking) pingSpikeAt = now;
        metric.flash = live && ping >= 0 ? flash(pingSpikeAt, now) : 0;
        HudPulse.tint(HudPulse.Style.Subtle, resting, criticalColor.get(), metric.flash, metric.color);
    }

    private void tpsDisplayColor(boolean flatlined, long now) {
        PerformanceMetric metric = metrics[2];
        metric.level = !live || tps < 0 ? 0 : flatlined || tps < TPS_CRITICAL ? 2 : tps < TPS_WARN ? 1 : 0;
        SettingColor resting = !live || tps < 0 ? titleColor.get() : metric.level == 2 ? criticalColor.get()
            : metric.level == 1 ? warnColor.get() : tpsColor.get();
        if (live && tps >= 0 && tpsDipping) tpsDipAt = now;
        metric.flash = live && tps >= 0 ? flash(tpsDipAt, now) : 0;
        HudPulse.tint(HudPulse.Style.Subtle, resting, criticalColor.get(), metric.flash, metric.color);
    }

    private void fpsDisplayColor(long now) {
        PerformanceMetric metric = metrics[0];
        metric.level = fps < criticalFps.get() ? 2 : fps < warnFps.get() ? 1 : 0;
        SettingColor resting = metric.level == 2 ? criticalColor.get() : metric.level == 1 ? warnColor.get() : fpsColor.get();
        if (fpsFalling) fpsDropAt = now;
        metric.flash = flash(fpsDropAt, now);
        HudPulse.tint(HudPulse.Style.Subtle, resting, criticalColor.get(), metric.flash, metric.color);
    }

    private static float flash(long at, long now) {
        if (at == 0L) return 0.0f;
        long elapsed = now - at;
        if (elapsed >= SPIKE_HOLD_MS) return 0.0f;
        return 1.0f - (float) elapsed / SPIKE_HOLD_MS;
    }

    private void sampleFps(int fps, long now) {
        if (now - lastFpsSampleAt < FPS_SAMPLE_EVERY_MS) return;
        lastFpsSampleAt = now;
        fpsHistory[fpsHead] = fps;
        fpsHead = (fpsHead + 1) % FPS_HISTORY_CAPACITY;
        if (fpsFilled < FPS_HISTORY_CAPACITY) fpsFilled++;
    }

    private float fpsBaseline() {
        if (fpsFilled == 0) return Float.NaN;
        float sum = 0.0f;
        for (int i = 0; i < fpsFilled; i++) sum += fpsHistory[i];
        return sum / fpsFilled;
    }

    private static float average(int[] samples, int count) {
        if (count <= 0) return Float.NaN;
        long sum = 0;
        for (int i = 0; i < count; i++) sum += samples[i];
        return (float) sum / count;
    }

    private static float average(float[] samples, int count) {
        if (count <= 0) return Float.NaN;
        float sum = 0.0f;
        for (int i = 0; i < count; i++) sum += samples[i];
        return sum / count;
    }

    private static float halfSpread(int[] samples, int count) {
        if (count <= 0) return 0.0f;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            if (samples[i] < min) min = samples[i];
            if (samples[i] > max) max = samples[i];
        }
        return (max - min) / 2.0f;
    }

    private static float spread(float[] samples, int count) {
        if (count <= 0) return 0.0f;
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            if (samples[i] < min) min = samples[i];
            if (samples[i] > max) max = samples[i];
        }
        return max - min;
    }

}
