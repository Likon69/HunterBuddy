package com.hunterbuddy.util;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * The translucent panel and neon halo the status HUDs sit on.
 *
 * <p>Shared for the same reason the flight maths is: two elements meant to read as a pair have
 * to be drawn by one piece of code, or they drift apart the first time either is adjusted. It
 * carries its own settings too, so the pair cannot end up with different defaults.
 *
 * <p>The halo is concentric rectangles growing outward with the alpha falling off as
 * {@code 1 - t²}, which is what gives a soft edge rather than visible steps.
 */
public final class HudGlowPanel {
    /** How urgent the line is, and therefore how loud the halo should be. */
    public enum Severity {
        OK,
        WARN,
        CRITICAL
    }

    private final Setting<Boolean> background;
    private final Setting<SettingColor> backgroundColor;
    private final Setting<Double> padding;
    private final Setting<Boolean> glow;
    private final Setting<Integer> glowLayers;
    private final Setting<Double> glowSpread;
    private final Setting<Integer> glowAlpha;
    private final Setting<Boolean> glowFollowsState;
    private final Setting<Boolean> glowIntensifies;
    private final Setting<SettingColor> glowColor;
    private final Setting<SettingColor> okColor;
    private final Setting<SettingColor> warnColor;
    private final Setting<SettingColor> criticalColor;

    /**
     * Registers the panel settings into a group.
     *
     * <p>Built rather than static so both elements get the same set with the same defaults from
     * one place, while each keeps its own saved values.
     */
    public HudGlowPanel(SettingGroup group) {
        background = group.add(new BoolSetting.Builder()
            .name("background")
            .description("Draw a translucent panel behind the line.")
            .defaultValue(true).build());

        backgroundColor = group.add(new ColorSetting.Builder()
            .name("background-color").description("Colour of that panel.")
            .defaultValue(new SettingColor(0, 0, 0, 120))
            .visible(background::get).build());

        padding = group.add(new DoubleSetting.Builder()
            .name("padding").description("Space between the text and the edge of the panel.")
            .defaultValue(4.0).min(0.0).max(20.0).sliderRange(0.0, 12.0).build());

        glow = group.add(new BoolSetting.Builder()
            .name("glow").description("Soft halo around the panel.")
            .defaultValue(true).build());

        glowLayers = group.add(new IntSetting.Builder()
            .name("glow-layers")
            .description("How many rings make up the halo. More is smoother and slightly costlier.")
            .defaultValue(4).min(1).max(12).sliderRange(1, 8)
            .visible(glow::get).build());

        glowSpread = group.add(new DoubleSetting.Builder()
            .name("glow-spread").description("How far each ring reaches beyond the last, in pixels.")
            .defaultValue(3.0).min(0.5).max(16.0).sliderRange(1.0, 8.0)
            .visible(glow::get).build());

        glowAlpha = group.add(new IntSetting.Builder()
            .name("glow-alpha").description("Opacity of the innermost ring.")
            .defaultValue(90).min(4).max(255).sliderRange(10, 200)
            .visible(glow::get).build());

        glowFollowsState = group.add(new BoolSetting.Builder()
            .name("glow-follows-state")
            .description("Take the halo colour from the worst field on the line, instead of one fixed colour.")
            .defaultValue(true).visible(glow::get).build());

        glowIntensifies = group.add(new BoolSetting.Builder()
            .name("glow-intensifies")
            .description("Grow the halo as things get worse. A halo that is always bright stops being read after a minute; one that only swells when it matters pulls the eye at the right moment.")
            .defaultValue(true).visible(glow::get).build());

        glowColor = group.add(new ColorSetting.Builder()
            .name("glow-color").description("Fixed halo colour, used when it does not follow the state.")
            .defaultValue(new SettingColor(90, 200, 255, 255))
            .visible(() -> glow.get() && !glowFollowsState.get()).build());

        okColor = group.add(new ColorSetting.Builder()
            .name("glow-ok-color").description("Halo colour while everything is fine.")
            .defaultValue(new SettingColor(90, 220, 130, 255))
            .visible(() -> glow.get() && glowFollowsState.get()).build());

        warnColor = group.add(new ColorSetting.Builder()
            .name("glow-warn-color").description("Halo colour once a field is low.")
            .defaultValue(new SettingColor(255, 190, 70, 255))
            .visible(() -> glow.get() && glowFollowsState.get()).build());

        criticalColor = group.add(new ColorSetting.Builder()
            .name("glow-critical-color").description("Halo colour once something is actually wrong.")
            .defaultValue(new SettingColor(255, 70, 70, 255))
            .visible(() -> glow.get() && glowFollowsState.get()).build());
    }

    public double padding() {
        return padding.get();
    }

    /**
     * Draws the halo and then the panel, around a box of content.
     *
     * <p>Call before the text: both are opaque enough to hide it, and the HUD renderer has no
     * depth to sort them by — the only ordering is the order of the calls.
     *
     * @param x,y,w,h the content box; the padding is added here rather than by the caller
     */
    public void draw(HudRenderer renderer, double x, double y, double w, double h, Severity severity) {
        double pad = padding.get();
        double bx = x - pad;
        double by = y - pad;
        double bw = w + pad * 2;
        double bh = h + pad * 2;

        if (glow.get()) {
            SettingColor tint = glowFollowsState.get() ? colorFor(severity) : glowColor.get();
            int layers = glowLayers.get();
            double spread = glowSpread.get();

            // A quiet halo when nothing is wrong, a loud one when something is. Fixed intensity
            // is available for anyone who prefers it, but the point of the halo is to be worth
            // looking at, and one that never changes is one you stop seeing.
            double boost = glowIntensifies.get()
                ? switch (severity) {
                    case OK -> 0.55;
                    case WARN -> 1.0;
                    case CRITICAL -> 1.6;
                }
                : 1.0;

            int base = (int) (glowAlpha.get() * boost);

            // Outermost first: the rings overlap, and the faint wide ones have to go down before
            // the bright narrow ones or they wash them out.
            for (int i = layers; i >= 1; i--) {
                double expansion = spread * i * (glowIntensifies.get() ? Math.max(0.6, boost) : 1.0);
                double t = (double) (i - 1) / layers;
                int alpha = Math.max(4, Math.min(255, (int) (base * (1.0 - t * t))));

                renderer.quad(bx - expansion, by - expansion,
                    bw + expansion * 2, bh + expansion * 2,
                    new Color(tint.r, tint.g, tint.b, alpha));
            }
        }

        if (background.get()) {
            renderer.quad(bx, by, bw, bh, backgroundColor.get());
        }
    }

    /**
     * The same halo, round.
     *
     * <p>A rectangular glow around a disc shows four corners of colour hanging off
     * nothing. Same settings, same layer maths, same severity: only the shape
     * changes, drawn from a radial sprite the caller supplies so this utility
     * keeps knowing nothing about any one HUD's assets.
     *
     * <p>Drawn inline rather than posted, because the shape batch flushes at the
     * end of the element: a texture queued behind it would land on top of the very
     * panel it is meant to sit behind.
     */
    public void drawRound(HudRenderer renderer, net.minecraft.util.Identifier disc,
                          double x, double y, double diameter, Severity severity) {
        double pad = padding.get();
        double cx = x + diameter / 2.0;
        double cy = y + diameter / 2.0;

        if (glow.get()) {
            SettingColor tint = glowFollowsState.get() ? colorFor(severity) : glowColor.get();
            int layers = glowLayers.get();
            double spread = glowSpread.get();

            double boost = glowIntensifies.get()
                ? switch (severity) {
                    case OK -> 0.55;
                    case WARN -> 1.0;
                    case CRITICAL -> 1.6;
                }
                : 1.0;

            int base = (int) (glowAlpha.get() * boost);

            for (int i = layers; i >= 1; i--) {
                double expansion = spread * i * (glowIntensifies.get() ? Math.max(0.6, boost) : 1.0);
                double t = (double) (i - 1) / layers;
                int alpha = Math.max(4, Math.min(255, (int) (base * (1.0 - t * t))));
                double r = diameter / 2.0 + pad + expansion;

                renderer.texture(disc, cx - r, cy - r, r * 2.0, r * 2.0,
                    new Color(tint.r, tint.g, tint.b, alpha));
            }
        }

        if (background.get()) {
            double r = diameter / 2.0 + pad;
            renderer.texture(disc, cx - r, cy - r, r * 2.0, r * 2.0, backgroundColor.get());
        }
    }

    private SettingColor colorFor(Severity severity) {
        return switch (severity) {
            case OK -> okColor.get();
            case WARN -> warnColor.get();
            case CRITICAL -> criticalColor.get();
        };
    }

    /** The loudest of the two, so a line takes the state of its worst field. */
    public static Severity worst(Severity a, Severity b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
