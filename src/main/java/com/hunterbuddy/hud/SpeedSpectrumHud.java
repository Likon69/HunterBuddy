package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import com.hunterbuddy.util.SessionStats;
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
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.Vec3d;

/**
 * An equalizer that dances to the flight instead of to music.
 *
 * <p>The bars are synthesized — layered sines, no audio anywhere — but their energy is real:
 * amplitude follows ground speed, and a rocket boost slams the whole rack up for a second.
 * Standing still it lies almost flat, which is exactly the point.
 */
public class SpeedSpectrumHud extends HudElement {
    public static final HudElementInfo<SpeedSpectrumHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "SpeedSpectrum",
        "Equalizer bars driven by your speed, spiking on rocket boosts.",
        SpeedSpectrumHud::new);

    public enum Style {
        /** Bottom-anchored equalizer bars — the original. */
        Bars,
        /** Bars grown from the centre line, up and down, like a hardware analyzer. */
        Mirror,
        /** A ribbon tracing the tips over a faint floor. */
        Wave,
        /** Bars with a bright highlight sweeping across the rack. */
        Pulse
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Style> style = sgGeneral.add(new EnumSetting.Builder<Style>()
        .name("style").description("How the rack animates.")
        .defaultValue(Style.Bars).build());

    private final Setting<Integer> bars = sgGeneral.add(new IntSetting.Builder()
        .name("bars").description("How many bars.")
        .defaultValue(24).min(8).max(48).sliderRange(12, 36).build());

    private final Setting<Integer> barWidth = sgGeneral.add(new IntSetting.Builder()
        .name("bar-width").description("Pixels per bar.")
        .defaultValue(4).min(2).max(10).sliderRange(3, 8).build());

    private final Setting<Double> spectrumHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("height").description("Height of the rack.")
        .defaultValue(26.0).min(14.0).max(80.0).sliderRange(18.0, 60.0).build());

    private final Setting<Boolean> reactToRockets = sgGeneral.add(new BoolSetting.Builder()
        .name("react-to-rockets").description("Slam the rack on each rocket you fire.")
        .defaultValue(true).build());

    private final Setting<Boolean> showSpeed = sgGeneral.add(new BoolSetting.Builder()
        .name("show-speed").description("The km/h line under the rack.")
        .defaultValue(true).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<SettingColor> tipColor = sgColors.add(new ColorSetting.Builder()
        .name("tip-color").description("The top of each bar.")
        .defaultValue(new SettingColor(126, 200, 255, 235)).build());

    private final Setting<SettingColor> baseColor = sgColors.add(new ColorSetting.Builder()
        .name("base-color").description("The bottom of each bar.")
        .defaultValue(new SettingColor(42, 95, 143, 200)).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").description("The speed line.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("The number in it.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);
    private final HudPulse.Tracker pulse = new HudPulse.Tracker();

    private int lastRockets = -1;
    private long boostAt;
    private long lastFrameAt = System.currentTimeMillis();

    public SpeedSpectrumHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        long now = System.currentTimeMillis();
        double dt = Math.min(0.25, (now - lastFrameAt) / 1000.0);
        lastFrameAt = now;

        double speedBs;

        if (MeteorClient.mc.player != null && MeteorClient.mc.world != null) {
            Vec3d velocity = MeteorClient.mc.player.getVelocity();
            speedBs = pulse.ease("speed", Math.hypot(velocity.x, velocity.z) * 20.0, dt, 0.35);

            int rockets = SessionStats.get().rocketsUsed();
            if (reactToRockets.get() && lastRockets >= 0 && rockets > lastRockets) boostAt = now;
            lastRockets = rockets;
        } else if (isInEditor()) {
            speedBs = 38.0 + 14.0 * Math.sin(now / 2600.0);
        } else {
            setSize(0.0, 0.0);
            return;
        }

        // 55 b/s is about 200 km/h — the loud end of the scale for a pitch40 cruise.
        double energy = Math.min(1.0, speedBs / 55.0);
        double boost = boostAt == 0L ? 0.0 : Math.max(0.0, 1.0 - (now - boostAt) / 900.0);
        double factor = Math.max(0.05, energy + boost * 0.4);

        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double pad = panel.padding();
        int barCount = bars.get();
        double bw = barWidth.get();
        double gap = 2.0;
        double rackH = spectrumHeight.get();
        double w = barCount * bw + (barCount - 1) * gap;
        double lineH = renderer.textHeight(shadow, scale);
        double h = rackH + (showSpeed.get() ? 2.0 + lineH : 0.0);

        panel.draw(renderer, x + pad, y + pad, w, h, HudGlowPanel.Severity.OK);

        double left = x + pad;
        double top = y + pad;
        double t = now / 1000.0;

        SettingColor tip = tipColor.get();
        SettingColor base = baseColor.get();

        double[] amp = new double[barCount];
        for (int i = 0; i < barCount; i++) {
            double wave = Math.abs(0.55 * Math.sin(t * 1.7 + i * 0.55)
                + 0.45 * Math.sin(t * 3.1 + i * 1.3));
            amp[i] = Math.min(1.0, wave * factor + boost * 0.25);
        }

        switch (style.get()) {
            case Bars -> drawBars(renderer, amp, left, top, bw, gap, rackH, tip, base);
            case Mirror -> drawMirror(renderer, amp, left, top, bw, gap, rackH, tip, base);
            case Wave -> drawWave(renderer, amp, left, top, bw, gap, rackH, tip, base);
            case Pulse -> drawPulse(renderer, amp, left, top, bw, gap, rackH, tip, base, now, w);
        }

        if (showSpeed.get()) {
            double ty = top + rackH + 2.0;
            String value = String.format("%.0f", speedBs * 3.6);
            double valueW = renderer.textWidth("888", shadow, scale);
            double totalW = valueW + renderer.textWidth(" km/h", shadow, scale);
            double tx = left + (w - totalW) / 2.0;

            renderer.text(value, tx + valueW - renderer.textWidth(value, shadow, scale), ty,
                valueColor.get(), shadow, scale);
            renderer.text(" km/h", tx + valueW, ty, labelColor.get(), shadow, scale);
        }

        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    private void drawBars(HudRenderer renderer, double[] amp, double left, double top,
                          double bw, double gap, double rackH, SettingColor tip, SettingColor base) {
        for (int i = 0; i < amp.length; i++) {
            double barH = Math.max(1.5, amp[i] * rackH);
            double bx = left + i * (bw + gap);
            renderer.quad(bx, top + rackH - barH, bw, barH, col(tip), col(tip), col(base), col(base));
        }
    }

    /** Grown from the centre both ways: base at the middle, tip at each end. */
    private void drawMirror(HudRenderer renderer, double[] amp, double left, double top,
                            double bw, double gap, double rackH, SettingColor tip, SettingColor base) {
        double mid = top + rackH / 2.0;
        for (int i = 0; i < amp.length; i++) {
            double half = Math.max(0.75, amp[i] * rackH / 2.0);
            double bx = left + i * (bw + gap);
            renderer.quad(bx, mid - half, bw, half, col(tip), col(tip), col(base), col(base));
            renderer.quad(bx, mid, bw, half, col(base), col(base), col(tip), col(tip));
        }
    }

    /** A ribbon tracing the tips, with a soft column under each and a faint floor line. */
    private void drawWave(HudRenderer renderer, double[] amp, double left, double top,
                          double bw, double gap, double rackH, SettingColor tip, SettingColor base) {
        double step = bw + gap;
        double half = bw / 2.0;
        renderer.quad(left, top + rackH - 1.0, Math.max(1.0, (amp.length - 1) * step + bw), 1.0,
            new Color(base.r, base.g, base.b, Math.max(0, base.a / 3)));

        Color line = col(tip);
        double prevX = left + half;
        double prevY = top + rackH - Math.max(1.0, amp[0] * rackH);
        for (int i = 1; i < amp.length; i++) {
            double px = left + i * step + half;
            double py = top + rackH - Math.max(1.0, amp[i] * rackH);
            renderer.line(prevX, prevY, px, py, line);
            renderer.quad(px - 0.5, py, 1.0, top + rackH - py, col(tip), col(tip), col(base), col(base));
            prevX = px;
            prevY = py;
        }
    }

    /** Equalizer bars with a highlight sweeping left to right on the clock. */
    private void drawPulse(HudRenderer renderer, double[] amp, double left, double top,
                           double bw, double gap, double rackH, SettingColor tip, SettingColor base,
                           long now, double w) {
        double sweep = left + (now % 1400L) / 1400.0 * w;
        double reach = Math.max(1.0, w * 0.14);
        for (int i = 0; i < amp.length; i++) {
            double bx = left + i * (bw + gap);
            double barH = Math.max(1.5, amp[i] * rackH);
            double glow = Math.max(0.0, 1.0 - Math.abs(bx + bw / 2.0 - sweep) / reach);
            Color t2 = new Color(
                Math.min(255, tip.r + (int) (glow * 90)),
                Math.min(255, tip.g + (int) (glow * 90)),
                Math.min(255, tip.b + (int) (glow * 90)),
                tip.a);
            renderer.quad(bx, top + rackH - barH, bw, barH, t2, t2, col(base), col(base));
        }
    }

    private static Color col(SettingColor c) {
        return new Color(c.r, c.g, c.b, c.a);
    }
}
