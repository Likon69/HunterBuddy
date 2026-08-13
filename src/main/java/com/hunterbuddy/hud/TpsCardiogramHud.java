package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import com.hunterbuddy.util.TpsSampler;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * A minute of server TPS drawn as a heart trace.
 *
 * <p>The trace is the real sampled history; the cardiogram dressing on top is honest theatre —
 * a beat whose tempo follows the tick rate, so a struggling server audibly (visibly) slows,
 * and a flatline the moment ticks stop arriving, which is the one signal 2b2t sends most.
 */
public class TpsCardiogramHud extends HudElement {
    public static final HudElementInfo<TpsCardiogramHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "TpsCardiogram",
        "Server TPS as a scrolling cardiogram with a flatline on lag.",
        TpsCardiogramHud::new);

    public enum Style {
        Cardiogram,
        Sparkline
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Style> style = sgGeneral.add(new EnumSetting.Builder<Style>()
        .name("style").description("Cardiogram adds the sweeping beat; Sparkline is just the trace.")
        .defaultValue(Style.Cardiogram).build());

    private final Setting<Double> width = sgGeneral.add(new DoubleSetting.Builder()
        .name("width").description("Width of the trace, in pixels.")
        .defaultValue(232.0).min(120.0).max(400.0).sliderRange(160.0, 320.0).build());

    private final Setting<Double> traceHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("height").description("Height of the trace, in pixels.")
        .defaultValue(36.0).min(20.0).max(80.0).sliderRange(24.0, 60.0).build());

    private final Setting<Boolean> showGrid = sgGeneral.add(new BoolSetting.Builder()
        .name("grid").description("Faint vertical rules behind the trace.")
        .defaultValue(true).build());

    private final Setting<com.hunterbuddy.util.HudPulse.Style> animationStyle = sgGeneral.add(
        new EnumSetting.Builder<com.hunterbuddy.util.HudPulse.Style>()
            .name("animation-style")
            .description("Subtle keeps the movement small. Lively lengthens the beat's trail, blinks harder, and makes the TPS figure hop when the server turns bad.")
            .defaultValue(com.hunterbuddy.util.HudPulse.Style.Subtle).build());

    private final Setting<Boolean> beatGlow = sgGeneral.add(new BoolSetting.Builder()
        .name("beat-glow")
        .description("The sweeping beat leaves a fading trail behind it. Cardiogram style only.")
        .defaultValue(true).build());

    private final Setting<Boolean> flatlineBlink = sgGeneral.add(new BoolSetting.Builder()
        .name("flatline-blink")
        .description("Blink the flatline while ticks have stopped, so a stalled server cannot be missed.")
        .defaultValue(true).build());

    private final Setting<Boolean> pulseWhenCritical = sgGeneral.add(new BoolSetting.Builder()
        .name("pulse-when-critical")
        .description("Breathe the TPS figure while the server is below the critical rate or flatlined.")
        .defaultValue(true).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").description("Labels.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("Values.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> traceColor = sgColors.add(new ColorSetting.Builder()
        .name("trace-color").description("The trace while the server is alive.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());

    private final Setting<SettingColor> flatlineColor = sgColors.add(new ColorSetting.Builder()
        .name("flatline-color").description("The trace while ticks have stopped.")
        .defaultValue(new SettingColor(255, 80, 80, 255)).build());

    private final Setting<SettingColor> gridColor = sgColors.add(new ColorSetting.Builder()
        .name("grid-color").description("The vertical rules.")
        .defaultValue(new SettingColor(110, 240, 130, 28)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);
    private final HudPulse.Tracker pulse = new HudPulse.Tracker();

    public TpsCardiogramHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        boolean live = MeteorClient.mc.world != null;

        float[] history;
        float current;
        float worst;
        float sinceLastTick;

        if (live) {
            TpsSampler sampler = TpsSampler.get();
            history = sampler.history();
            current = sampler.current();
            worst = sampler.worst();
            sinceLastTick = sampler.timeSinceLastTick();
        } else if (isInEditor()) {
            history = new float[120];
            long now = System.currentTimeMillis();
            for (int i = 0; i < history.length; i++) {
                history[i] = (float) (17.0 + 2.0 * Math.sin((now / 500.0 + i) / 6.0)
                    + Math.sin(i * 3.7) * 0.6);
            }
            current = history[history.length - 1];
            worst = 14.8f;
            sinceLastTick = 0.1f;
        } else {
            setSize(0.0, 0.0);
            return;
        }

        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double pad = panel.padding();
        double w = width.get();
        double traceH = traceHeight.get();
        double lineH = renderer.textHeight(shadow, scale);
        double h = traceH + 4.0 + lineH;

        boolean flatlined = sinceLastTick > 1.0f;

        HudGlowPanel.Severity severity = flatlined || current < 9.0f ? HudGlowPanel.Severity.CRITICAL
            : current < 14.0f ? HudGlowPanel.Severity.WARN
            : HudGlowPanel.Severity.OK;

        panel.draw(renderer, x + pad, y + pad, w, h, severity);

        double left = x + pad;
        double top = y + pad;

        if (showGrid.get()) {
            for (double gx = 0.0; gx <= w; gx += 24.0) {
                renderer.line(left + gx, top, left + gx, top + traceH, gridColor.get());
            }
        }

        SettingColor trace = flatlined ? flatlineColor.get() : traceColor.get();

        if (history.length >= 2) {
            double stepX = w / (history.length - 1);
            double prevX = left;
            double prevY = traceY(history[0], top, traceH);

            for (int i = 1; i < history.length; i++) {
                double px = left + i * stepX;
                double py = traceY(history[i], top, traceH);
                renderer.line(prevX, prevY, px, py, trace);
                prevX = px;
                prevY = py;
            }
        }

        // The flatline is drawn as what it is: the trace pinned at the level ticks stopped at,
        // extended from the right edge for as long as the silence has lasted.
        if (flatlined) {
            double flatW = Math.min(w, sinceLastTick / 60.0 * w);
            double flatY = traceY(0.0f, top, traceH) - 1.0;
            SettingColor flat = flatlineColor.get();

            // Blinking rather than glowing: a steady red bar becomes part of the furniture within
            // a minute, and this is the one reading that should stay uncomfortable.
            int alpha = flatlineBlink.get() ? (int) (flat.a * blink()) : flat.a;
            renderer.quad(left + w - flatW, flatY, flatW, 2.0, new Color(flat.r, flat.g, flat.b, alpha));
        }

        if (style.get() == Style.Cardiogram && !flatlined) {
            drawBeat(renderer, left, top, w, traceH, current);
        }

        double ty = top + traceH + 4.0;
        double tx = left;

        boolean ailing = severity == HudGlowPanel.Severity.CRITICAL;
        float sevFlash = pulse.freshness("severity", severity.name(), 500L);
        double bump = HudPulse.bounce(animationStyle.get(), sevFlash, scale);

        SettingColor tpsBase = flatlined ? flatlineColor.get() : valueColor.get();
        Color tpsColor = pulseWhenCritical.get() && ailing
            ? new Color(tpsBase.r, tpsBase.g, tpsBase.b, (int) (tpsBase.a * breath()))
            : new Color(tpsBase.r, tpsBase.g, tpsBase.b, tpsBase.a);

        tx = label(renderer, "TPS ", tx, ty, shadow, scale);
        tx = fixed(renderer, String.format("%.1f", Math.min(20.0f, current)), "88.8", tx, ty + bump,
            tpsColor, shadow, scale);
        tx = label(renderer, "  min 60s ", tx, ty, shadow, scale);
        tx = fixed(renderer, String.format("%.1f", Math.min(20.0f, worst)), "88.8", tx, ty, valueColor.get(), shadow, scale);

        if (flatlined) {
            tx = label(renderer, "  lag ", tx, ty, shadow, scale);
            fixed(renderer, String.format("%.1f s", sinceLastTick), "88.8 s", tx, ty, flatlineColor.get(), shadow, scale);
        }

        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    private double traceY(float tps, double top, double traceH) {
        double norm = Math.max(0.0, Math.min(1.0, tps / 20.0));
        return top + 2.0 + (1.0 - norm) * (traceH - 4.0);
    }

    /**
     * The QRS spike sweeping the trace, its tempo set by the tick rate itself.
     *
     * <p>At twenty TPS it beats once a second; at ten, every two. Decoration, but decoration
     * that carries the number — you feel the server slowing before you read it.
     */
    private void drawBeat(HudRenderer renderer, double left, double top, double w, double traceH, float current) {
        double period = 1000.0 * (20.0 / Math.max(2.0f, current));
        double phase = (System.currentTimeMillis() % (long) period) / period;
        double beatX = left + phase * w;

        double amp = (traceH * 0.45) * (current / 20.0);
        double baseY = top + traceH * 0.55;

        SettingColor color = traceColor.get();

        // The trail is drawn first so the live spike sits on top of it, and each ghost is a copy
        // of the spike a moment further back — the shape is the same, only fainter, which reads as
        // the same mark moving rather than as several marks.
        if (beatGlow.get()) {
            int ghosts = animationStyle.get() == HudPulse.Style.Lively ? 4 : 2;

            for (int i = ghosts; i >= 1; i--) {
                double ghostX = beatX - i * (w * 0.018);
                if (ghostX < left) continue;

                int alpha = (int) (235.0 * (1.0 - i / (double) (ghosts + 1)) * 0.55);
                drawSpike(renderer, ghostX, baseY, amp, left, w, new Color(color.r, color.g, color.b, alpha));
            }
        }

        drawSpike(renderer, beatX, baseY, amp, left, w, new Color(color.r, color.g, color.b, 235));
    }

    /** One QRS mark. Clipped to the trace, so nothing is ever drawn past the right edge. */
    private void drawSpike(HudRenderer renderer, double beatX, double baseY, double amp,
                           double left, double w, Color color) {
        if (beatX + 7.0 > left + w) return;

        renderer.line(beatX, baseY, beatX + 2.0, baseY - amp, color);
        renderer.line(beatX + 2.0, baseY - amp, beatX + 4.0, baseY + amp * 0.5, color);
        renderer.line(beatX + 4.0, baseY + amp * 0.5, beatX + 7.0, baseY, color);
    }

    /** A slow sine on the clock, for a figure that should look alive while it is unwell. */
    private static double breath() {
        return 0.55 + 0.45 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 380.0));
    }

    /** Faster and deeper than the breath: this one is meant to nag. */
    private double blink() {
        double depth = animationStyle.get() == HudPulse.Style.Lively ? 0.75 : 0.45;
        double period = animationStyle.get() == HudPulse.Style.Lively ? 240.0 : 380.0;

        return 1.0 - depth * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / period));
    }

    private double label(HudRenderer renderer, String s, double tx, double ty, boolean shadow, double scale) {
        renderer.text(s, tx, ty, labelColor.get(), shadow, scale);
        return tx + renderer.textWidth(s, shadow, scale);
    }

    /** A value in a reserved slot sized by a template, so the row holds still as digits roll. */
    private double fixed(HudRenderer renderer, String s, String template, double tx, double ty,
                         Color color, boolean shadow, double scale) {
        renderer.text(s, tx, ty, color, shadow, scale);
        return tx + Math.max(renderer.textWidth(template, shadow, scale), renderer.textWidth(s, shadow, scale));
    }
}
