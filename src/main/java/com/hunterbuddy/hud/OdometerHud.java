package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudGlowPanel;
import com.hunterbuddy.util.HudPulse;
import com.hunterbuddy.util.LifetimeStats;
import com.hunterbuddy.util.SessionStats;
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
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/**
 * The distance as a mechanical drum counter, with a lifetime total underneath.
 *
 * <p>The drums show the session; the line below carries the number that makes it an odometer
 * rather than a stopwatch — every session ever, folded into one figure that only goes up.
 * The last drum rolls with the fractional block, because a counter that visibly turns is the
 * whole pleasure of the thing.
 */
public class OdometerHud extends HudElement {
    public static final HudElementInfo<OdometerHud> INFO = new HudElementInfo<>(
        HunterBuddyAddon.HUD_GROUP, "Odometer",
        "Session distance on mechanical drums, lifetime total below.",
        OdometerHud::new);

    /** How the counter's digits are drawn. Only the digits — the lines below never change. */
    public enum DigitStyle {
        Drums,
        Flat,
        LCD
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMilestones = settings.createGroup("Milestones");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Double> milestoneKm = sgMilestones.add(new DoubleSetting.Builder()
        .name("milestone-km").description("Session distance between milestones, in kilometres.")
        .defaultValue(10.0).min(0.5).sliderRange(1.0, 100.0).build());

    private final Setting<Double> lifetimeMilestoneKm = sgMilestones.add(new DoubleSetting.Builder()
        .name("lifetime-milestone-km").description("All-time distance between milestones, in kilometres.")
        .defaultValue(100.0).min(1.0).sliderRange(10.0, 1000.0).build());

    private final Setting<Boolean> milestoneSound = sgMilestones.add(new BoolSetting.Builder()
        .name("milestone-sound").description("Play a sound when a milestone is passed.")
        .defaultValue(true).build());

    private final Setting<Double> milestoneVolume = sgMilestones.add(new DoubleSetting.Builder()
        .name("milestone-volume").description("Volume of that sound.")
        .defaultValue(1.0).min(0.0).max(1.0).sliderRange(0.0, 1.0)
        .visible(milestoneSound::get).build());

    private final Setting<Boolean> milestoneFlash = sgMilestones.add(new BoolSetting.Builder()
        .name("milestone-flash").description("Flash the digits when a milestone is passed.")
        .defaultValue(true).build());
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Integer> drums = sgGeneral.add(new IntSetting.Builder()
        .name("drums").description("How many digit drums.")
        .defaultValue(7).min(4).max(9).sliderRange(5, 9).build());

    private final Setting<DigitStyle> digitStyle = sgGeneral.add(
        new meteordevelopment.meteorclient.settings.EnumSetting.Builder<DigitStyle>()
            .name("digit-style")
            .description("Drums is the mechanical counter. Flat is bare digits. LCD draws them as seven-segment figures.")
            .defaultValue(DigitStyle.Drums).build());

    private final Setting<Boolean> showLifetime = sgGeneral.add(new BoolSetting.Builder()
        .name("show-lifetime").description("The all-time total under the drums.")
        .defaultValue(true).build());

    private final Setting<Boolean> showFlightTime = sgGeneral.add(new BoolSetting.Builder()
        .name("show-flight-time")
        .description("Time spent gliding, this session and all time, under the distance line.")
        .defaultValue(true).build());

    private final Setting<Double> textScale = sgGeneral.add(new DoubleSetting.Builder()
        .name("text-scale").description("Scale of the digits and text.")
        .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build());

    private final Setting<Boolean> textShadow = sgGeneral.add(new BoolSetting.Builder()
        .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

    private final Setting<SettingColor> digitColor = sgColors.add(new ColorSetting.Builder()
        .name("digit-color").description("The steady drums.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> liveDigitColor = sgColors.add(new ColorSetting.Builder()
        .name("live-digit-color").description("The last two drums, the ones that visibly move.")
        .defaultValue(new SettingColor(255, 200, 80, 255)).build());

    private final Setting<SettingColor> labelColor = sgColors.add(new ColorSetting.Builder()
        .name("label-color").description("The line under the drums.")
        .defaultValue(new SettingColor(150, 150, 150, 255)).build());

    private final Setting<SettingColor> valueColor = sgColors.add(new ColorSetting.Builder()
        .name("value-color").description("The numbers in that line.")
        .defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final HudGlowPanel panel = new HudGlowPanel(sgPanel);

    /**
     * Last distances seen, so a milestone fires on the crossing rather than on the condition.
     *
     * <p>NaN until the first frame has been read: the opening frame has no previous reading to
     * have crossed anything from, and treating it as zero would announce a milestone for every
     * one already behind you the moment the element appears.
     */
    private double lastSession = Double.NaN;
    private double lastLifetime = Double.NaN;

    /** Counts of milestones passed, used purely as the value the flash tracker watches. */
    private int sessionMilestones;
    private int lifetimeMilestones;

    private final HudPulse.Tracker milestonePulse = new HudPulse.Tracker();

    public OdometerHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double session;
        double lifetime;
        long sessionFlight;
        long lifetimeFlight;

        if (MeteorClient.mc.world != null) {
            session = SessionStats.get().distance();
            lifetime = LifetimeStats.get().totalDistance();
            sessionFlight = SessionStats.get().flightSeconds();
            lifetimeFlight = LifetimeStats.get().totalFlightSeconds();

            // Only on the live path. The editor's counter is a fabrication that climbs on its own,
            // and would ring the bell every few seconds for distance nobody flew.
            checkMilestones(session, lifetime);
        } else if (isInEditor()) {
            // Rolls on its own in the editor, at cruising speed.
            session = 412_806.0 + (System.currentTimeMillis() % 3_600_000L) / 1000.0 * 47.0;
            lifetime = 8_122_407.0 + session - 412_806.0;
            sessionFlight = 8_040L;
            lifetimeFlight = 1_231_200L;
        } else {
            setSize(0.0, 0.0);
            return;
        }

        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double pad = panel.padding();

        DigitStyle style = digitStyle.get();

        double digitScale = scale * 1.3;
        double digitW = renderer.textWidth("8", shadow, digitScale);
        double digitH = renderer.textHeight(shadow, digitScale);

        // Each style carries its own cell. Flat has no case to sit in, so it takes none of the
        // width one would need; the seven-segment figure is drawn rather than typed, so its
        // proportions come from the height instead of from the font.
        double cellW = switch (style) {
            case Drums -> digitW + 6.0 * scale;
            case Flat -> digitW;
            case LCD -> (digitH + 4.0 * scale) * 0.55 + 4.0 * scale;
        };
        double cellH = style == DigitStyle.Flat ? digitH : digitH + 4.0 * scale;
        double gap = 2.0 * scale;

        int count = drums.get();
        int separators = (count - 1) / 3;
        double groupGap = 3.0 * scale;
        double drumsW = count * cellW + (count - 1) * gap + separators * groupGap;
        double lineH = renderer.textHeight(shadow, scale);
        // The caption sits above the drums, so it belongs in the height too — left out, the
        // panel is short by a line and the bottom text falls outside it.
        double captionH = renderer.textHeight(shadow, scale * 0.8) + 2.0 * scale;
        // The first line under the drums sits a little lower than the ones that follow it, so the
        // block reads as attached to the counter rather than floating below it.
        double firstGap = 3.0 * scale;
        double nextGap = 2.0 * scale;

        double h = captionH + cellH
            + (showLifetime.get() ? firstGap + lineH : 0.0)
            + (showFlightTime.get() ? (showLifetime.get() ? nextGap : firstGap) + lineH : 0.0);

        double w = Math.max(drumsW, Math.max(
            showLifetime.get() ? renderer.textWidth(bottomTemplate(), shadow, scale) : 0.0,
            showFlightTime.get() ? renderer.textWidth(flightTemplate(), shadow, scale) : 0.0));

        // The all-time mark is the rarer event, so it burns longer and harder than the session one.
        double sessionFlash = milestoneFlash.get()
            ? milestonePulse.freshness("session", String.valueOf(sessionMilestones), 600L) * 0.6
            : 0.0;
        double lifetimeFlash = milestoneFlash.get()
            ? milestonePulse.freshness("lifetime", String.valueOf(lifetimeMilestones), 1200L)
            : 0.0;
        double flash = Math.max(sessionFlash, lifetimeFlash);

        panel.draw(renderer, x + pad, y + pad, w, h,
            flash > 0.0 ? HudGlowPanel.Severity.WARN : HudGlowPanel.Severity.OK);

        double left = x + pad + (w - drumsW) / 2.0;
        double top = y + pad;

        // The drums are a number and nothing else; without this they could be counting anything.
        renderer.text("BLOCKS TRAVELLED", x + pad + (w - renderer.textWidth("BLOCKS TRAVELLED", shadow, scale * 0.8)) / 2.0,
            top, labelColor.get(), shadow, scale * 0.8);
        top += renderer.textHeight(shadow, scale * 0.8) + 2.0 * scale;

        long whole = (long) session;
        double frac = session - whole;
        String padded = String.format("%0" + count + "d", whole % pow10(count));

        double cx = left;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                cx += cellW + gap;
                // A wider gap every three digits from the right, so the count reads in groups.
                if ((count - i) % 3 == 0) cx += groupGap;
            }

            boolean liveDrum = i >= count - 2;
            char digit = padded.charAt(i);
            Color color = milestoneTint(liveDrum ? liveDigitColor.get() : digitColor.get(), flash);
            boolean rolling = i == count - 1;

            if (style == DigitStyle.LCD) {
                drawDrumCell(renderer, cx, top, cellW, cellH, liveDrum);
                drawLcdDigit(renderer, cx, top, cellW, cellH, digit, color, rolling, frac);
                continue;
            }

            if (style == DigitStyle.Drums) drawDrumCell(renderer, cx, top, cellW, cellH, liveDrum);

            double dx = cx + (cellW - digitW) / 2.0;
            double dy = top + (cellH - digitH) / 2.0;

            if (rolling) {
                // The rolling drum: the current digit fades out as the next fades in, driven by
                // the fractional block. A scissor would slide them; the renderer has none, and a
                // crossfade reads as the same motion at this size.
                char next = (char) ('0' + (digit - '0' + 1) % 10);
                int outAlpha = (int) (color.a * (1.0 - frac));
                int inAlpha = (int) (color.a * frac);
                renderer.text(String.valueOf(digit), dx, dy - frac * 2.0,
                    new Color(color.r, color.g, color.b, outAlpha), shadow, digitScale);
                renderer.text(String.valueOf(next), dx, dy + (1.0 - frac) * 2.0,
                    new Color(color.r, color.g, color.b, inAlpha), shadow, digitScale);
            } else {
                renderer.text(String.valueOf(digit), dx, dy, color, shadow, digitScale);
            }
        }

        double ty = top + cellH + firstGap;

        if (showLifetime.get()) {
            // Kilometres, not raw blocks. The drums already give the exact count; repeating it
            // underneath in full said nothing extra, and "12 345" reads as a quantity of
            // something unnamed rather than as a distance.
            drawPair(renderer, "this session ", formatDistance(session), formatDistance(lifetime),
                x + pad, ty, w, shadow, scale);
            ty += lineH + nextGap;
        }

        if (showFlightTime.get()) {
            // The distance says how far; this says how long it took to be in the air for it. On a
            // hunt that runs for weeks, the two together are what the odometer is for.
            drawPair(renderer, "flight  this session ", formatFlightTime(sessionFlight),
                formatFlightTime(lifetimeFlight), x + pad, ty, w, shadow, scale);
        }

        setSize(w + pad * 2.0, h + pad * 2.0);
    }

    /**
     * Notices a milestone being crossed, and marks it.
     *
     * <p>The test is on the crossing, not on the value: a counter sitting just past a mark would
     * otherwise satisfy a "past the mark" condition on every frame and ring continuously. Comparing
     * which interval the previous reading fell in against this one fires exactly once, at the step.
     */
    private void checkMilestones(double session, double lifetime) {
        if (crossed(lastSession, session, milestoneKm.get() * 1000.0)) {
            sessionMilestones++;
            playMilestone(false);
        }

        if (crossed(lastLifetime, lifetime, lifetimeMilestoneKm.get() * 1000.0)) {
            lifetimeMilestones++;
            playMilestone(true);
        }

        lastSession = session;
        lastLifetime = lifetime;
    }

    /** True only on the frame the value moves from one interval of {@code step} into the next. */
    private static boolean crossed(double previous, double now, double step) {
        if (Double.isNaN(previous) || step <= 0.0) return false;

        return Math.floor(now / step) > Math.floor(previous / step);
    }

    private void playMilestone(boolean lifetime) {
        if (!milestoneSound.get() || MeteorClient.mc.player == null) return;

        float volume = milestoneVolume.get().floatValue();
        if (volume <= 0.0f) return;

        // A session mark passes several times an hour, so it gets a click you can live with; the
        // all-time mark is worth looking up for.
        MeteorClient.mc.getSoundManager().play(lifetime
            ? PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, volume)
            : PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, volume));
    }

    /** Pulls a digit's colour toward the live colour while a milestone flash is running. */
    private Color milestoneTint(SettingColor base, double intensity) {
        if (intensity <= 0.0) return new Color(base.r, base.g, base.b, base.a);

        SettingColor accent = liveDigitColor.get();

        return new Color(
            (int) (base.r + (accent.r - base.r) * intensity),
            (int) (base.g + (accent.g - base.g) * intensity),
            (int) (base.b + (accent.b - base.b) * intensity),
            base.a);
    }

    /**
     * Which of the seven segments each figure lights, as a bit per segment.
     *
     * <p>Bit order is the conventional a b c d e f g: top, top-right, bottom-right, bottom,
     * bottom-left, top-left, middle.
     */
    private static final int[] LCD_SEGMENTS = {
        0x3F, // 0: abcdef
        0x06, // 1: bc
        0x5B, // 2: abged
        0x4F, // 3: abgcd
        0x66, // 4: fgbc
        0x6D, // 5: afgcd
        0x7D, // 6: afgedc
        0x07, // 7: abc
        0x7F, // 8: abcdefg
        0x6F  // 9: abcfgd
    };

    /**
     * One seven-segment figure, drawn as rectangles.
     *
     * <p>The dark segments are drawn too, and that is the whole effect: a real display shows the
     * unlit bars faintly whatever it is reading, so an eight is always ghosted behind a one.
     *
     * <p>The rolling figure is cross-faded whole rather than segment by segment. Fading segments
     * individually reads as a fault in the display — bars flickering on and off — where fading
     * the figure reads as a figure changing.
     */
    private void drawLcdDigit(HudRenderer renderer, double cx, double cy, double w, double h,
                              char digit, Color color, boolean rolling, double frac) {
        double inset = 3.0;
        double gx = cx + inset;
        double gy = cy + inset;
        double gw = w - inset * 2.0;
        double gh = h - inset * 2.0;
        double t = Math.max(1.5, gh * 0.12);

        Color off = new Color(40, 45, 55, 255);
        int lit = LCD_SEGMENTS[digit - '0'];

        for (int s = 0; s < 7; s++) {
            drawSegment(renderer, gx, gy, gw, gh, t, s, off);
        }

        if (!rolling) {
            paintLit(renderer, gx, gy, gw, gh, t, lit, new Color(color.r, color.g, color.b, color.a));
            return;
        }

        int next = LCD_SEGMENTS[(digit - '0' + 1) % 10];
        paintLit(renderer, gx, gy, gw, gh, t, lit,
            new Color(color.r, color.g, color.b, (int) (color.a * (1.0 - frac))));
        paintLit(renderer, gx, gy, gw, gh, t, next,
            new Color(color.r, color.g, color.b, (int) (color.a * frac)));
    }

    private void paintLit(HudRenderer renderer, double gx, double gy, double gw, double gh, double t,
                          int mask, Color color) {
        for (int s = 0; s < 7; s++) {
            if ((mask & (1 << s)) != 0) drawSegment(renderer, gx, gy, gw, gh, t, s, color);
        }
    }

    /** One bar of the display. Horizontals are inset by a bar's width so the corners meet. */
    private void drawSegment(HudRenderer renderer, double gx, double gy, double gw, double gh, double t,
                             int segment, Color color) {
        double half = gh / 2.0;
        double armH = half - t * 1.5;

        switch (segment) {
            case 0 -> renderer.quad(gx + t, gy, gw - t * 2.0, t, color);
            case 1 -> renderer.quad(gx + gw - t, gy + t, t, armH, color);
            case 2 -> renderer.quad(gx + gw - t, gy + half + t / 2.0, t, armH, color);
            case 3 -> renderer.quad(gx + t, gy + gh - t, gw - t * 2.0, t, color);
            case 4 -> renderer.quad(gx, gy + half + t / 2.0, t, armH, color);
            case 5 -> renderer.quad(gx, gy + t, t, armH, color);
            default -> renderer.quad(gx + t, gy + half - t / 2.0, gw - t * 2.0, t, color);
        }
    }

    /** The cylinder illusion: dark at the rims, lit at the middle, done with two gradients. */
    private void drawDrumCell(HudRenderer renderer, double cx, double cy, double w, double h, boolean live) {
        Color rim = live ? new Color(20, 26, 36, 255) : new Color(10, 13, 18, 255);
        Color belly = live ? new Color(40, 49, 62, 255) : new Color(28, 34, 44, 255);
        Color border = live ? new Color(61, 71, 86, 255) : new Color(51, 60, 74, 255);

        renderer.quad(cx - 1.0, cy - 1.0, w + 2.0, h + 2.0, border);
        renderer.quad(cx, cy, w, h / 2.0, rim, rim, belly, belly);
        renderer.quad(cx, cy + h / 2.0, w, h / 2.0, belly, belly, rim, rim);
    }

    /** One centred "label value · all time value" line, the shape both bottom rows share. */
    private void drawPair(HudRenderer renderer, String label, String session, String lifetime,
                          double left, double ty, double w, boolean shadow, double scale) {
        String middle = "  ·  all time ";
        double lineW = renderer.textWidth(label + session + middle + lifetime, shadow, scale);
        double tx = left + (w - lineW) / 2.0;

        tx = draw(renderer, label, tx, ty, labelColor.get(), shadow, scale);
        tx = draw(renderer, session, tx, ty, valueColor.get(), shadow, scale);
        tx = draw(renderer, middle, tx, ty, labelColor.get(), shadow, scale);
        draw(renderer, lifetime, tx, ty, valueColor.get(), shadow, scale);
    }

    private String bottomTemplate() {
        return "this session 8888.8 km  ·  all time 8888.8 km";
    }

    private String flightTemplate() {
        return "flight  this session 888d 88h  ·  all time 888d 88h";
    }

    /**
     * Flight time at the coarsest unit that still says something.
     *
     * <p>A lifetime total runs to weeks, where minutes and seconds are noise; a fresh session is
     * measured in minutes, where days would read as zero. Two significant units either way.
     */
    private static String formatFlightTime(long seconds) {
        if (seconds >= 86_400L) return String.format("%dd %dh", seconds / 86_400L, (seconds % 86_400L) / 3600L);
        if (seconds >= 3600L) return String.format("%dh %dm", seconds / 3600L, (seconds % 3600L) / 60L);
        if (seconds >= 60L) return String.format("%dm", seconds / 60L);
        return seconds + "s";
    }

    private static String formatDistance(double blocks) {
        return blocks >= 1000.0 ? String.format("%.1f km", blocks / 1000.0)
            : String.format("%.0f m", blocks);
    }

    private static long pow10(int n) {
        long out = 1L;
        for (int i = 0; i < n; i++) out *= 10L;
        return out;
    }

    private double draw(HudRenderer renderer, String s, double tx, double ty, Color color, boolean shadow, double scale) {
        renderer.text(s, tx, ty, color, shadow, scale);
        return tx + renderer.textWidth(s, shadow, scale);
    }
}
