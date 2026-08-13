package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import com.hunterbuddy.util.PingSampler;
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
 * The ping with its recent past: value, half a minute of sparkline, jitter, keepalive age.
 *
 * <p>No packet-loss percentage, deliberately — the client has no honest way to measure one. The
 * keepalive age stands in for it: when those stop coming the connection is failing, whatever
 * the last latency figure claimed.
 */
public class PingMeterHud extends HudElement {
    public static final HudElementInfo<PingMeterHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "PingMeter",
        "Ping with sparkline, jitter and keepalive freshness.",
        PingMeterHud::new);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Integer> warnPing = sgGeneral.add(new IntSetting.Builder()
        .name("warn-ping").description("Milliseconds above which the halo turns warn.")
        .defaultValue(150).min(50).max(1000).sliderRange(80, 400).build());

    private final Setting<Integer> criticalPing = sgGeneral.add(new IntSetting.Builder()
        .name("critical-ping").description("Milliseconds above which the halo turns critical.")
        .defaultValue(400).min(100).max(2000).sliderRange(200, 800).build());

    private final Setting<Double> sparkWidth = sgGeneral.add(new DoubleSetting.Builder()
        .name("sparkline-width").description("Width of the history sparkline.")
        .defaultValue(84.0).min(40.0).max(200.0).sliderRange(50.0, 140.0).build());

    private final Setting<HudPulse.Style> animationStyle = sgGeneral.add(
        new meteordevelopment.meteorclient.settings.EnumSetting.Builder<HudPulse.Style>()
            .name("animation-style")
            .description("Subtle keeps the movement small. Lively beats harder and makes the figure hop when the ping changes band.")
            .defaultValue(HudPulse.Style.Subtle).build());

    private final Setting<Boolean> heartbeat = sgGeneral.add(new BoolSetting.Builder()
        .name("heartbeat")
        .description("The dot at the end of the sparkline swells on every new sample. When samples stop, so does the beat.")
        .defaultValue(true).build());

    private final Setting<Boolean> flashOnChange = sgGeneral.add(new BoolSetting.Builder()
        .name("flash-on-change")
        .description("Flare the big figure when the ping crosses into another band.")
        .defaultValue(true).build());

    private final Setting<Boolean> pulseWhenCritical = sgGeneral.add(new BoolSetting.Builder()
        .name("pulse-when-critical")
        .description("Breathe the big figure while the ping is critical or the keepalives have gone quiet.")
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

    private final Setting<SettingColor> sparkColor = sgColors.add(new ColorSetting.Builder()
        .name("sparkline-color").description("The history line.")
        .defaultValue(new SettingColor(126, 200, 255, 255)).build());

    private final Setting<SettingColor> warnColor = sgColors.add(new ColorSetting.Builder()
        .name("warn-color").description("Values once they cross the warn threshold.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final Setting<SettingColor> criticalColor = sgColors.add(new ColorSetting.Builder()
        .name("critical-color").description("Values once they cross the critical threshold.")
        .defaultValue(new SettingColor(255, 80, 80, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);
    private final HudPulse.Tracker pulse = new HudPulse.Tracker();

    public PingMeterHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        int ping;
        int[] history;
        int jitter;
        double keepAliveAge;

        boolean live = MeteorClient.mc.world != null;

        if (live) {
            PingSampler sampler = PingSampler.get();
            ping = sampler.current();
            history = sampler.history();
            jitter = sampler.jitter();
            keepAliveAge = sampler.keepAliveAgeSeconds();
        } else if (isInEditor()) {
            ping = 118;
            history = new int[30];
            long now = System.currentTimeMillis();
            for (int i = 0; i < history.length; i++) {
                history[i] = 100 + (int) (30.0 * Math.abs(Math.sin(now / 900.0 + i * 0.7)));
            }
            jitter = 34;
            keepAliveAge = 3.0;
        } else {
            setSize(0.0, 0.0);
            return;
        }

        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double pad = panel.padding();
        double lineH = renderer.textHeight(shadow, scale);

        double bigScale = scale * 1.6;
        double bigH = renderer.textHeight(shadow, bigScale);
        double sparkW = sparkWidth.get();
        double sparkH = Math.max(14.0, bigH - 2.0);

        double pingSlot = renderer.textWidth("8888", shadow, bigScale);
        double msW = renderer.textWidth(" ms", shadow, scale);
        double w = pingSlot + msW + 6.0 * scale + sparkW;
        double h = bigH + 2.0 + lineH;

        HudGlowPanel.Severity severity =
            ping >= criticalPing.get() || keepAliveAge > 20.0 ? HudGlowPanel.Severity.CRITICAL
                : ping >= warnPing.get() || jitter > 80 ? HudGlowPanel.Severity.WARN
                : HudGlowPanel.Severity.OK;

        panel.draw(renderer, x + pad, y + pad, w, h, severity);

        double left = x + pad;
        double top = y + pad;

        // With no latency reported, the big figure becomes the one thing actually measured on
        // this side: how long ago the server last spoke. It answers the question the ping was
        // there to answer — is the line alive — without pretending to a number nobody sent.
        boolean hasMs = !live || PingSampler.get().hasLatency();

        Color pingColor = ping < 0 ? labelColor.get()
            : ping >= criticalPing.get() ? criticalColor.get()
            : ping >= warnPing.get() ? warnColor.get()
            : valueColor.get();

        // Watched by name rather than by value: crossing a threshold is the event worth showing,
        // and the number itself moves constantly without meaning anything has changed.
        float bandFlash = flashOnChange.get()
            ? pulse.freshness("band", severity.name(), 500L)
            : 0.0f;

        boolean ailing = severity == HudGlowPanel.Severity.CRITICAL;
        double breathe = pulseWhenCritical.get() && ailing ? breath() : 1.0;

        if (!hasMs) {
            String age = keepAliveAge < 0.0 ? "-" : String.format("%.0f", keepAliveAge);
            Color ageColor = keepAliveAge > 20.0 ? criticalColor.get()
                : keepAliveAge > 10.0 ? warnColor.get() : valueColor.get();

            drawBig(renderer, age, left, top, ageColor, bandFlash, breathe, shadow, bigScale, scale);
            renderer.text(" s idle", left + pingSlot, top + (bigH - lineH), labelColor.get(), shadow, scale);
        } else {
            drawBig(renderer, ping < 0 ? "-" : String.valueOf(ping), left, top, pingColor,
                bandFlash, breathe, shadow, bigScale, scale);
            renderer.text(" ms", left + pingSlot, top + (bigH - lineH), labelColor.get(), shadow, scale);
        }

        // Keyed on the newest sample: no new reading, no beat, which is exactly what a stalled
        // connection should look like.
        float beat = heartbeat.get() && history.length > 0
            ? pulse.freshness("beat", String.valueOf(history[history.length - 1]), 700L)
            : 0.0f;

        drawSparkline(renderer, history, left + pingSlot + msW + 6.0 * scale, top + 1.0, sparkW, sparkH, beat);

        double ty = top + bigH + 2.0;
        double tx = left;
        tx = label(renderer, "jitter ", tx, ty, shadow, scale);
        tx = fixed(renderer, "±" + jitter, "±888", tx, ty, jitter > 80 ? warnColor.get() : valueColor.get(), shadow, scale);
        tx = label(renderer, "  keepalive ", tx, ty, shadow, scale);

        String ka = keepAliveAge < 0.0 ? "-" : String.format("%.0f s", keepAliveAge);
        fixed(renderer, ka, "88 s", tx, ty,
            keepAliveAge > 20.0 ? criticalColor.get() : keepAliveAge > 10.0 ? warnColor.get() : valueColor.get(),
            shadow, scale);

        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    /**
     * The big figure, flared on a band change and breathing while the line is in trouble.
     *
     * <p>Drawn at a fixed origin whatever the animation is doing. The hop of the lively style is
     * vertical only and the flare is pure colour, so nothing here can move the " ms" beside it.
     */
    private void drawBig(HudRenderer renderer, String text, double left, double top, Color base,
                         float flash, double breathe, boolean shadow, double bigScale, double scale) {
        // Flared towards white rather than towards the band's own colour: the figure is already
        // painted in that colour, so tinting it towards itself would show nothing at all. The
        // white is the flash; the colour underneath is what the flash announces.
        Color colour = flash > 0.0f
            ? HudPulse.tint(animationStyle.get(), toSetting(base), new SettingColor(255, 255, 255, base.a), flash)
            : base;

        if (breathe < 1.0) {
            colour = new Color(colour.r, colour.g, colour.b, (int) (colour.a * breathe));
        }

        renderer.text(text, left, top + HudPulse.bounce(animationStyle.get(), flash, scale),
            colour, shadow, bigScale);
    }

    /** A slow sine on the clock, for something that should look alive while it is unwell. */
    private static double breath() {
        return 0.55 + 0.45 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 380.0));
    }

    private static SettingColor toSetting(Color color) {
        return new SettingColor(color.r, color.g, color.b, color.a);
    }

    /** Normalised to the window's own min/max: the shape of the wobble, not its absolute scale. */
    private void drawSparkline(HudRenderer renderer, int[] history, double left, double top, double w, double h,
                               float beat) {
        if (history.length < 2) return;

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int sample : history) {
            if (sample < min) min = sample;
            if (sample > max) max = sample;
        }
        int span = Math.max(10, max - min);

        double stepX = w / (history.length - 1);
        double prevX = left;
        double prevY = top + h - (history[0] - min) / (double) span * h;

        for (int i = 1; i < history.length; i++) {
            double px = left + i * stepX;
            double py = top + h - (history[i] - min) / (double) span * h;
            renderer.line(prevX, prevY, px, py, sparkColor.get());
            prevX = px;
            prevY = py;
        }

        SettingColor dot = sparkColor.get();

        // Swollen from its own centre, so the line it caps does not appear to move. The lively
        // style beats to the full amplitude; the subtle one takes half of it.
        double amplitude = animationStyle.get() == HudPulse.Style.Lively ? 1.0 : 0.5;
        double size = 3.0 * (1.0 + (HudPulse.pop(beat) - 1.0) * amplitude);

        // Brightening with the beat as well as growing: at this size a couple of pixels alone are
        // easy to miss, and the two together read as one pulse.
        int lift = (int) (beat * 90.0);
        Color colour = new Color(
            Math.min(255, dot.r + lift), Math.min(255, dot.g + lift), Math.min(255, dot.b + lift), 255);

        renderer.quad(prevX - size / 2.0, prevY - size / 2.0, size, size, colour);
    }

    private double label(HudRenderer renderer, String s, double tx, double ty, boolean shadow, double scale) {
        renderer.text(s, tx, ty, labelColor.get(), shadow, scale);
        return tx + renderer.textWidth(s, shadow, scale);
    }

    private double fixed(HudRenderer renderer, String s, String template, double tx, double ty,
                         Color color, boolean shadow, double scale) {
        renderer.text(s, tx, ty, color, shadow, scale);
        return tx + Math.max(renderer.textWidth(template, shadow, scale), renderer.textWidth(s, shadow, scale));
    }
}
