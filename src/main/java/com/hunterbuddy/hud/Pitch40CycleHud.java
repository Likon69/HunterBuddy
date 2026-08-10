package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.Pitch40;
import com.hunterbuddy.modules.Pitch40Classic;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * The pitch40 cycle drawn as the wave it is, with the flight's position on it.
 *
 * <p>ElytraStatus already gives the numbers; what it cannot give is rhythm. One period of the
 * climb-and-dive is drawn once, the current altitude is placed on whichever half the module says
 * it is in, and the eye reads "where in the breath am I" without any arithmetic.
 */
public class Pitch40CycleHud extends HudElement {
    public static final HudElementInfo<Pitch40CycleHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "Pitch40Cycle",
        "The pitch40 climb/dive cycle as a wave, with your position on it.",
        Pitch40CycleHud::new);

    private static final int CURVE_SEGMENTS = 32;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Double> width = sgGeneral.add(new DoubleSetting.Builder()
        .name("width").description("Width of the wave, in pixels.")
        .defaultValue(220.0).min(120.0).max(400.0).sliderRange(140.0, 320.0).build());

    private final Setting<Double> waveHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("wave-height").description("Height of the wave, in pixels.")
        .defaultValue(40.0).min(24.0).max(90.0).sliderRange(28.0, 70.0).build());

    private final Setting<Boolean> showCountdown = sgGeneral.add(new BoolSetting.Builder()
        .name("show-countdown").description("Estimate the seconds left before the cycle flips.")
        .defaultValue(true).build());

    private final Setting<Boolean> hideWhenInactive = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-when-inactive").description("Disappear entirely while the Pitch40 module is off.")
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

    private final Setting<SettingColor> waveColor = sgColors.add(new ColorSetting.Builder()
        .name("wave-color").description("The cycle curve.")
        .defaultValue(new SettingColor(255, 255, 255, 90)).build());

    private final Setting<SettingColor> markerColor = sgColors.add(new ColorSetting.Builder()
        .name("marker-color").description("Your position on the curve while inside the bounds.")
        .defaultValue(new SettingColor(110, 240, 130, 255)).build());

    private final Setting<SettingColor> ceilingColor = sgColors.add(new ColorSetting.Builder()
        .name("ceiling-color").description("The upper bound line.")
        .defaultValue(new SettingColor(255, 200, 80, 110)).build());

    private final Setting<SettingColor> floorColor = sgColors.add(new ColorSetting.Builder()
        .name("floor-color").description("The lower bound line.")
        .defaultValue(new SettingColor(255, 80, 80, 110)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);
    private final HudPulse.Tracker pulse = new HudPulse.Tracker();

    private long lastFrameAt = System.currentTimeMillis();

    public Pitch40CycleHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        long now = System.currentTimeMillis();
        double dt = Math.min(0.25, (now - lastFrameAt) / 1000.0);
        lastFrameAt = now;

        double[] bounds = activeBounds();
        boolean live = bounds != null
            && MeteorClient.mc.player != null && MeteorClient.mc.world != null;

        double lower;
        double upper;
        double altitude;
        boolean climbing;
        double verticalSpeed;

        if (live) {
            lower = bounds[0];
            upper = bounds[1];
            altitude = MeteorClient.mc.player.getY();
            verticalSpeed = pulse.ease("vy", MeteorClient.mc.player.getVelocity().y * 20.0, dt, 0.4);
            // Climb vs dive from actual vertical velocity, so the marker reads the same for both
            // Pitch40 and Pitch40Classic without either exposing an internal cycle flag.
            climbing = verticalSpeed >= 0.0;
        } else if (isInEditor()) {
            // A cycle that runs by itself, so the element can be judged and placed without a
            // flight behind it.
            lower = 120.0;
            upper = 260.0;
            double phase = (now % 8000L) / 8000.0;
            altitude = lower + (upper - lower) * (0.5 - 0.5 * Math.cos(phase * Math.PI * 2.0));
            climbing = phase < 0.5;
            verticalSpeed = (climbing ? 1 : -1) * 12.0 * Math.sin(phase * Math.PI * 2.0 > Math.PI
                ? phase * Math.PI * 2.0 - Math.PI : phase * Math.PI * 2.0);
        } else if (!hideWhenInactive.get()) {
            drawInactive(renderer);
            return;
        } else {
            setSize(0.0, 0.0);
            return;
        }

        double span = Math.max(1.0, upper - lower);
        double norm = Math.max(0.0, Math.min(1.0, (altitude - lower) / span));

        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double pad = panel.padding();
        double w = width.get();
        double curveH = waveHeight.get();
        double lineH = renderer.textHeight(shadow, scale);
        double h = curveH + 4.0 + lineH;

        HudGlowPanel.Severity severity = severity(live, altitude, lower, upper);
        panel.draw(renderer, x + pad, y + pad, w, h, severity);

        double left = x + pad;
        double top = y + pad;

        drawBoundLine(renderer, left, top, w, ceilingColor.get());
        drawBoundLine(renderer, left, top + curveH, w, floorColor.get());

        // One period of 1-cos: altitude low at both ends, high in the middle of the climb half.
        double prevX = left;
        double prevY = waveY(0.0, top, curveH);
        for (int i = 1; i <= CURVE_SEGMENTS; i++) {
            double t = (double) i / CURVE_SEGMENTS;
            double px = left + t * w;
            double py = waveY(t, top, curveH);
            renderer.line(prevX, prevY, px, py, waveColor.get());
            prevX = px;
            prevY = py;
        }

        // The marker sits at the exact parameter whose wave altitude matches the real one, on
        // the half the module says the flight is in — so it runs left to right through a climb
        // and keeps running, not jumping, into the dive.
        double tOnCurve = Math.acos(1.0 - 2.0 * norm) / (Math.PI * 2.0);
        double t = climbing ? tOnCurve : 1.0 - tOnCurve;
        double markerX = left + t * w;
        double markerY = waveY(t, top, curveH);

        boolean inBounds = altitude >= lower - 5.0 && altitude <= upper + 5.0;
        SettingColor marker = inBounds ? markerColor.get() : floorColor.get();
        int markerAlpha = (int) (170 + 85 * Math.sin(now / 250.0));
        renderer.quad(markerX - 2.5, markerY - 2.5, 5.0, 5.0,
            new Color(marker.r, marker.g, marker.b, Math.max(0, Math.min(255, markerAlpha))));

        double ty = top + curveH + 4.0;
        double tx = left;
        tx = text(renderer, "alt ", tx, ty, labelColor.get(), shadow, scale);
        tx = value(renderer, String.format("%.0f", altitude), tx, ty, valueColor.get(), shadow, scale, renderer.textWidth("8888", shadow, scale));
        tx = text(renderer, climbing ? " climb " : " dive ", tx, ty, climbing ? markerColor.get() : ceilingColor.get(), shadow, scale);
        tx = text(renderer, climbing ? "to " + String.format("%.0f", upper) : "to " + String.format("%.0f", lower),
            tx, ty, labelColor.get(), shadow, scale);

        if (showCountdown.get()) {
            String flip = flipEstimate(altitude, lower, upper, climbing, verticalSpeed);
            tx = text(renderer, " · flip ", tx, ty, labelColor.get(), shadow, scale);
            text(renderer, flip, tx, ty, valueColor.get(), shadow, scale);
        }

        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    /** {floor, ceiling} from whichever pitch40 module is active, or null if neither is. */
    private double[] activeBounds() {
        // ElytraAutoFly first, because it is the one that owns a cycle. It drives Pitch40Classic
        // itself — on to climb, off to descend — so asking Pitch40Classic instead made the
        // element vanish for the whole descent, exactly half of every cycle. Its own two
        // altitudes are the window, and they hold steady through both phases.
        com.hunterbuddy.modules.ElytraAutoFly autoFly =
            Modules.get().get(com.hunterbuddy.modules.ElytraAutoFly.class);
        if (autoFly != null && autoFly.isActive()) {
            return orderBounds(autoFly.minAltitude.get(), autoFly.maxAltitude.get());
        }

        Pitch40 p = Modules.get().get(Pitch40.class);
        if (p != null && p.isActive()) {
            return orderBounds(p.pitch40LowerBounds.get(), p.pitch40UpperBounds.get());
        }

        Pitch40Classic c = Modules.get().get(Pitch40Classic.class);
        if (c != null && c.isActive()) {
            return orderBounds(c.lowerBound.get(), c.upperBound.get());
        }

        return null;
    }

    /** Ordered low-to-high, so a module whose bounds are set either way round still draws right. */
    private static double[] orderBounds(double a, double b) {
        return new double[]{Math.min(a, b), Math.max(a, b)};
    }

    private double waveY(double t, double top, double curveH) {
        double norm = 0.5 - 0.5 * Math.cos(t * Math.PI * 2.0);
        return top + (1.0 - norm) * curveH;
    }

    /** Dashes drawn by hand: four on, three off. The renderer has lines and nothing dashed. */
    private void drawBoundLine(HudRenderer renderer, double left, double y, double w, SettingColor color) {
        for (double px = 0.0; px < w; px += 7.0) {
            renderer.line(left + px, y, left + Math.min(px + 4.0, w), y, color);
        }
    }

    private String flipEstimate(double altitude, double lower, double upper, boolean climbing, double verticalSpeed) {
        double remaining = climbing ? upper - altitude : altitude - lower;
        double rate = climbing ? verticalSpeed : -verticalSpeed;

        if (rate < 0.5 || remaining < 0.0) return "-";

        double seconds = remaining / rate;
        return seconds > 99.0 ? "-" : String.format("%.0f s", seconds);
    }

    private HudGlowPanel.Severity severity(boolean live, double altitude, double lower, double upper) {
        if (!live) return HudGlowPanel.Severity.OK;

        // The module's own emergency threshold: ten under the floor is where it panic-boosts.
        if (altitude < lower - 10.0) return HudGlowPanel.Severity.CRITICAL;
        if (altitude < lower - 5.0 || altitude > upper + 5.0) return HudGlowPanel.Severity.WARN;

        return HudGlowPanel.Severity.OK;
    }

    private void drawInactive(HudRenderer renderer) {
        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double pad = panel.padding();
        String label = "Pitch40 off";
        double w = renderer.textWidth(label, shadow, scale);
        double h = renderer.textHeight(shadow, scale);

        panel.draw(renderer, x + pad, y + pad, w, h, HudGlowPanel.Severity.OK);
        renderer.text(label, x + pad, y + pad, labelColor.get(), shadow, scale);
        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    private double text(HudRenderer renderer, String s, double tx, double ty, Color color, boolean shadow, double scale) {
        renderer.text(s, tx, ty, color, shadow, scale);
        return tx + renderer.textWidth(s, shadow, scale);
    }

    /** Draws a value inside a reserved width, so the line does not tremble as digits change. */
    private double value(HudRenderer renderer, String s, double tx, double ty,
                         Color color, boolean shadow, double scale, double reserved) {
        renderer.text(s, tx, ty, color, shadow, scale);
        return tx + Math.max(reserved, renderer.textWidth(s, shadow, scale));
    }
}
