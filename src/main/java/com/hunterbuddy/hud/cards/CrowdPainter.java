package com.hunterbuddy.hud.cards;

import com.hunterbuddy.hud.glass.GlassPainter;
import com.hunterbuddy.hud.glass.GlassStyle;
import com.hunterbuddy.hud.toast.ModuleToastBlur;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class CrowdPainter {
    private static final double HEAD = 22;
    private static final double GAP = 4;
    private static final double RING = 1.5;
    private static final double PADDING = 8;
    private final GlassPainter glass = new GlassPainter();

    public int columns(double width, double scale) {
        double cell = cell(scale);
        return Math.max(1, (int) ((width - 2 * PADDING * scale + GAP * scale) / (cell + GAP * scale)));
    }

    public double height(int count, double width, double scale) {
        int rows = (count + columns(width, scale) - 1) / columns(width, scale);
        return 2 * PADDING * scale + rows * cell(scale) + Math.max(0, rows - 1) * GAP * scale;
    }

    public void card(HudRenderer renderer, CardStyle style, double x, double y, double width,
                     double height) {
        double s = style.scale();
        boolean flat = style.glassStyle() == GlassStyle.Flat;
        if (style.blur()) ModuleToastBlur.INSTANCE.draw(x, y, width, height, flat ? 0 : GlassPainter.radius(s), 1);
        if (flat) glass.flatCard(renderer, x, y, width, height, s, 1, style.background());
        else glass.card(renderer, x, y, width, height, s, 1, style.reflection(), style.background());
    }

    public void head(HudRenderer renderer, CardStyle style, PlayerTrack track, Color ring, int index, int columns,
                     double x, double y) {
        double s = style.scale();
        double cell = cell(s);
        double headX = x + PADDING * s + index % columns * (cell + GAP * s);
        double headY = y + PADDING * s + index / columns * (cell + GAP * s);
        CardIcons.head(renderer, track.skin(), headX + RING * s, headY + RING * s, HEAD * s, 1);
        glass.outline(renderer, headX, headY, cell, cell, style.glassStyle() == GlassStyle.Flat ? 0 : 3 * s, RING * s, ring);
    }

    private static double cell(double scale) {
        return (HEAD + 2 * RING) * scale;
    }
}
