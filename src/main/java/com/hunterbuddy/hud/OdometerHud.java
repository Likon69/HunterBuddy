package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.HudGlowPanel;
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

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final SettingGroup sgPanel = settings.createGroup("Panel");

    private final Setting<Integer> drums = sgGeneral.add(new IntSetting.Builder()
        .name("drums").description("How many digit drums.")
        .defaultValue(7).min(4).max(9).sliderRange(5, 9).build());

    private final Setting<Boolean> showLifetime = sgGeneral.add(new BoolSetting.Builder()
        .name("show-lifetime").description("The all-time total under the drums.")
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

    public OdometerHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double session;
        double lifetime;

        if (MeteorClient.mc.world != null) {
            session = SessionStats.get().distance();
            lifetime = LifetimeStats.get().totalDistance();
        } else if (isInEditor()) {
            // Rolls on its own in the editor, at cruising speed.
            session = 412_806.0 + (System.currentTimeMillis() % 3_600_000L) / 1000.0 * 47.0;
            lifetime = 8_122_407.0 + session - 412_806.0;
        } else {
            setSize(0.0, 0.0);
            return;
        }

        boolean shadow = textShadow.get();
        double scale = textScale.get();
        double pad = panel.padding();

        double digitScale = scale * 1.3;
        double digitW = renderer.textWidth("8", shadow, digitScale);
        double digitH = renderer.textHeight(shadow, digitScale);
        double cellW = digitW + 6.0 * scale;
        double cellH = digitH + 4.0 * scale;
        double gap = 2.0 * scale;

        int count = drums.get();
        int separators = (count - 1) / 3;
        double groupGap = 3.0 * scale;
        double drumsW = count * cellW + (count - 1) * gap + separators * groupGap;
        double lineH = renderer.textHeight(shadow, scale);
        // The caption sits above the drums, so it belongs in the height too — left out, the
        // panel is short by a line and the bottom text falls outside it.
        double captionH = renderer.textHeight(shadow, scale * 0.8) + 2.0 * scale;
        double h = captionH + cellH + (showLifetime.get() ? 3.0 * scale + lineH : 0.0);
        double w = Math.max(drumsW,
            showLifetime.get() ? renderer.textWidth(bottomTemplate(), shadow, scale) : 0.0);

        panel.draw(renderer, x + pad, y + pad, w, h, HudGlowPanel.Severity.OK);

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
            drawDrumCell(renderer, cx, top, cellW, cellH, liveDrum);

            char digit = padded.charAt(i);
            SettingColor color = liveDrum ? liveDigitColor.get() : digitColor.get();
            double dx = cx + (cellW - digitW) / 2.0;
            double dy = top + (cellH - digitH) / 2.0;

            if (i == count - 1) {
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

        if (showLifetime.get()) {
            double ty = top + cellH + 3.0 * scale;
            // Kilometres, not raw blocks. The drums already give the exact count; repeating it
            // underneath in full said nothing extra, and "12 345" reads as a quantity of
            // something unnamed rather than as a distance.
            String a = "this session ";
            String b = formatDistance(session);
            String c = "  ·  all time ";
            String d = formatDistance(lifetime);

            double lineW = renderer.textWidth(a + b + c + d, shadow, scale);
            double tx = x + pad + (w - lineW) / 2.0;

            tx = draw(renderer, a, tx, ty, labelColor.get(), shadow, scale);
            tx = draw(renderer, b, tx, ty, valueColor.get(), shadow, scale);
            tx = draw(renderer, c, tx, ty, labelColor.get(), shadow, scale);
            draw(renderer, d, tx, ty, valueColor.get(), shadow, scale);
        }

        setSize(w + pad * 2.0, h + pad * 2.0);
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

    private String bottomTemplate() {
        return "this session 8888.8 km  ·  all time 8888.8 km";
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
