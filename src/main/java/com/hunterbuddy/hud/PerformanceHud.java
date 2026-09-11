package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudPulse;
import com.hunterbuddy.util.PingSampler;
import com.hunterbuddy.util.TpsSampler;
import java.util.Locale;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * FPS, ping and server TPS, in one line.
 *
 * <p>Ping and TPS are read from {@link PingSampler} and {@link TpsSampler} — the same samplers
 * the ping and TPS HUDs already read, so this one never disagrees with them. FPS has no sampler
 * of its own, so it is read straight off the client every frame.
 *
 * <p>Ping and TPS fall back to a dash once the player or the network handler is gone, which is
 * the case at the title screen and while connecting. FPS keeps reading in both places, since it
 * stays meaningful there.
 *
 * <p>Each value has a resting colour by level: the ping thresholds are settings with PingMeterHud's
 * defaults and ranges, TPS uses TpsCardiogramHud's split (and its flatline, and its clamp at 20),
 * and FPS has thresholds of its own, set below a 60 fps cap so a capped client does not sit orange.
 * On top of that, a value that jumps or drops well past its own recent level flashes toward the
 * critical colour and fades over {@link #SPIKE_HOLD_MS}. The newest sample is measured against the
 * ones before it, margin included, so a spike never widens the margin it is judged by.
 */
public class PerformanceHud extends HudElement {
    public static final HudElementInfo<PerformanceHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "Performance",
        "FPS, ping and server TPS in one line.",
        PerformanceHud::new);

    // The same severity split TpsCardiogramHud already uses.
    private static final float TPS_WARN = 14.0f;
    private static final float TPS_CRITICAL = 9.0f;
    private static final float TPS_FLATLINE_SECONDS = 1.0f;
    // A catch-up burst after a lag reads past 20; TpsCardiogramHud clamps it the same way.
    private static final float TPS_MAX = 20.0f;

    // How long a spike or drop holds the flash, once it fires.
    private static final long SPIKE_HOLD_MS = 2000L;

    // A ping jump counts once it clears both a fixed floor and twice the jitter of the samples
    // before it (half their spread, the figure PingSampler calls jitter).
    private static final int PING_SPIKE_FLOOR_MS = 40;
    private static final float PING_SPIKE_JITTER_MULT = 2.0f;
    private static final int PING_BASELINE_MIN_SAMPLES = 5;

    private static final float TPS_DIP_FLOOR = 2.0f;
    private static final float TPS_DIP_SPREAD_MULT = 1.0f;
    private static final int TPS_BASELINE_MIN_SAMPLES = 6;

    // FPS has no sampler, so a tiny local history is kept here, sampled once a second rather
    // than every frame, the same way PingSampler and TpsSampler sample their own.
    private static final long FPS_SAMPLE_EVERY_MS = 1000L;
    private static final int FPS_HISTORY_CAPACITY = 10;
    private static final int FPS_BASELINE_MIN_SAMPLES = 4;
    private static final float FPS_MIN_BASELINE = 15.0f;
    private static final float FPS_DROP_FLOOR = 15.0f;
    private static final float FPS_DROP_FRACTION = 0.30f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showTitle = sgGeneral.add(new BoolSetting.Builder()
        .name("show-title").description("Display the HUD title.")
        .defaultValue(false).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
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

    private final float[] fpsHistory = new float[FPS_HISTORY_CAPACITY];
    private int fpsHead;
    private int fpsFilled;
    private long lastFpsSampleAt;
    private boolean wasLive;

    // When each value last spiked or dropped; 0 for never.
    private long pingSpikeAt;
    private long tpsDipAt;
    private long fpsDropAt;

    public PerformanceHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean shadow = textShadow.get();
        double scale = textScale.get();
        long now = System.currentTimeMillis();

        // Ping and TPS need a live connection to mean anything; FPS does not, so it is read
        // unconditionally and stays visible at the title screen and while connecting.
        boolean live = MeteorClient.mc.player != null && MeteorClient.mc.getNetworkHandler() != null;
        if (live && !wasLive) {
            // A world has just loaded: the menu's frame rate is no baseline for it, and would read
            // as a drop for the first seconds.
            fpsHead = 0;
            fpsFilled = 0;
            lastFpsSampleAt = 0L;
        }
        wasLive = live;

        int fps = MeteorClient.mc.getCurrentFps();
        sampleFps(fps, now);
        int ping = live ? PingSampler.get().current() : -1;
        float tps = live ? Math.min(TPS_MAX, TpsSampler.get().current()) : -1.0f;
        boolean flatlined = live && TpsSampler.get().timeSinceLastTick() > TPS_FLATLINE_SECONDS;

        String fpsText = String.valueOf(fps);
        String pingText = ping < 0 ? "-" : ping + " ms";
        String tpsText = tps < 0.0f ? "-" : String.format(Locale.ROOT, "%.1f", tps);

        Color fpsDraw = fpsDisplayColor(fps, now);
        Color pingDraw = pingDisplayColor(live, ping, now);
        Color tpsDraw = tpsDisplayColor(live, tps, flatlined, now);

        double textHeight = renderer.textHeight(shadow, scale);
        double spacing = 2.0;
        double curY = y;
        double maxWidth = 0.0;
        double height = 0.0;

        if (showTitle.get()) {
            String title = "Performance";
            renderer.text(title, x, curY, titleColor.get(), shadow, scale);
            maxWidth = Math.max(maxWidth, renderer.textWidth(title, shadow, scale));
            curY += textHeight + spacing;
            height += textHeight + spacing;
        }

        double tx = x;
        tx = label(renderer, "FPS ", tx, curY, fpsDraw, shadow, scale);
        tx = fixed(renderer, fpsText, "888", tx, curY, fpsDraw, shadow, scale);
        tx = label(renderer, "  Ping ", tx, curY, pingDraw, shadow, scale);
        tx = fixed(renderer, pingText, "8888 ms", tx, curY, pingDraw, shadow, scale);
        tx = label(renderer, "  TPS ", tx, curY, tpsDraw, shadow, scale);
        tx = fixed(renderer, tpsText, "88.8", tx, curY, tpsDraw, shadow, scale);

        height += textHeight;
        maxWidth = Math.max(maxWidth, tx - x);

        setSize(maxWidth, height);
    }

    /**
     * Resting colour by the warn and critical ping settings. A spike is the newest sample jumping
     * past the average of the ones before it by more than twice their jitter (and a 40 ms floor),
     * all read from one copy of the history so the value tested is the one the baseline excludes.
     */
    private Color pingDisplayColor(boolean live, int ping, long now) {
        if (!live || ping < 0) return titleColor.get();

        SettingColor resting = ping >= criticalPing.get() ? criticalColor.get()
            : ping >= warnPing.get() ? warnColor.get()
            : pingColor.get();

        int[] history = PingSampler.get().history();
        int before = history.length - 1;
        if (before >= PING_BASELINE_MIN_SAMPLES) {
            float baseline = average(history, before);
            float margin = Math.max(PING_SPIKE_FLOOR_MS, halfSpread(history, before) * PING_SPIKE_JITTER_MULT);
            if (history[before] - baseline > margin) pingSpikeAt = now;
        }

        return HudPulse.tint(HudPulse.Style.Subtle, resting, criticalColor.get(), flash(pingSpikeAt, now));
    }

    /**
     * Resting colour by TpsCardiogramHud's split and its flatline. A dip is the newest sample falling
     * below the average of the ones before it by more than their spread (and a 2 TPS floor), every
     * sample clamped at 20 first so a catch-up burst neither raises the baseline nor widens it.
     */
    private Color tpsDisplayColor(boolean live, float tps, boolean flatlined, long now) {
        if (!live || tps < 0.0f) return titleColor.get();

        SettingColor resting = flatlined || tps < TPS_CRITICAL ? criticalColor.get()
            : tps < TPS_WARN ? warnColor.get()
            : tpsColor.get();

        float[] history = TpsSampler.get().history();
        int before = history.length - 1;
        if (before >= TPS_BASELINE_MIN_SAMPLES) {
            float[] clamped = new float[history.length];
            for (int i = 0; i < history.length; i++) clamped[i] = Math.min(TPS_MAX, history[i]);
            float baseline = average(clamped, before);
            float margin = Math.max(TPS_DIP_FLOOR, spread(clamped, before) * TPS_DIP_SPREAD_MULT);
            if (baseline - clamped[before] > margin) tpsDipAt = now;
        }

        return HudPulse.tint(HudPulse.Style.Subtle, resting, criticalColor.get(), flash(tpsDipAt, now));
    }

    /**
     * Resting colour by the warn and critical FPS settings. A drop is the live frame rate falling
     * below the few seconds of history by more than 30 % of it (and 15 fps), which ordinary
     * frame-time jitter never reaches: getCurrentFps() is itself a one-second count.
     */
    private Color fpsDisplayColor(int fps, long now) {
        SettingColor resting = fps < criticalFps.get() ? criticalColor.get()
            : fps < warnFps.get() ? warnColor.get()
            : fpsColor.get();

        float baseline = fpsBaseline();
        if (fpsFilled >= FPS_BASELINE_MIN_SAMPLES && baseline >= FPS_MIN_BASELINE
            && (baseline - fps) > Math.max(FPS_DROP_FLOOR, baseline * FPS_DROP_FRACTION)) {
            fpsDropAt = now;
        }

        return HudPulse.tint(HudPulse.Style.Subtle, resting, criticalColor.get(), flash(fpsDropAt, now));
    }

    /** 1 the moment a spike is seen, fading to 0 over {@link #SPIKE_HOLD_MS}; seen again, it starts over. */
    private static float flash(long at, long now) {
        if (at == 0L) return 0.0f;
        long elapsed = now - at;
        if (elapsed >= SPIKE_HOLD_MS) return 0.0f;
        return 1.0f - (float) elapsed / SPIKE_HOLD_MS;
    }

    /** Pushes one FPS sample a second, not one a frame — a few seconds of level, not of noise. */
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

    /** Half the spread of the first {@code count} samples: PingSampler's jitter, over a chosen slice. */
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

    private double label(HudRenderer renderer, String s, double tx, double ty, Color color, boolean shadow, double scale) {
        renderer.text(s, tx, ty, color, shadow, scale);
        return tx + renderer.textWidth(s, shadow, scale);
    }

    /** A value in a reserved slot sized by a template, so the row holds still as digits roll. */
    private double fixed(HudRenderer renderer, String s, String template, double tx, double ty,
                         Color color, boolean shadow, double scale) {
        renderer.text(s, tx, ty, color, shadow, scale);
        return tx + Math.max(renderer.textWidth(template, shadow, scale), renderer.textWidth(s, shadow, scale));
    }
}
