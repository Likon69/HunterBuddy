package com.hunterbuddy.hud.glass;

import com.hunterbuddy.hud.text.HudText;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class GlassPainter {
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color BLACK = new Color(0, 0, 0);
    private final Color ink = new Color();
    private final Color top = new Color();
    private final Color bottom = new Color();

    public static double pillRadius(double scale) {
        return Math.max(4, 7 * scale);
    }

    public static double radius(double scale) {
        return Math.max(6, 10 * scale);
    }

    public static double centered(HudRenderer renderer, double center, double textScale) {
        return center - HudText.textHeight(renderer, false, textScale) / 2;
    }

    public static String fit(HudRenderer renderer, String value, double available, double scale) {
        if (HudText.textWidth(renderer, value, false, scale) <= available) return value;
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String shortened = value.substring(0, end) + "...";
            if (HudText.textWidth(renderer, shortened, false, scale) <= available) return shortened;
        }
        return "...";
    }

    public void card(HudRenderer renderer, double x, double y, double width, double height, double scale,
                     double alpha, boolean reflection, Color background) {
        double radius = radius(scale);
        rounded(renderer, x, y, width, height, radius, tint(background, alpha));
        outline(renderer, x, y, width, height, radius, 0.7 * scale, tint(WHITE, alpha * 0.12));
        if (reflection) {
            double startY = y + scale;
            double endY = y + height * 0.5;
            double innerRadius = Math.max(0, radius - scale);
            for (int band = 0; band < 16; band++) {
                double upper = startY + (endY - startY) * band / 16;
                double lower = startY + (endY - startY) * (band + 1) / 16;
                double offset = Math.max(0, y + radius - upper);
                double inset = innerRadius - Math.sqrt(Math.max(0, innerRadius * innerRadius - offset * offset));
                set(top, WHITE, alpha * 0.07 * (1 - (double) band / 16));
                set(bottom, WHITE, alpha * 0.07 * (1 - (double) (band + 1) / 16));
                renderer.quad(x + scale + inset, upper, width - 2 * (scale + inset), lower - upper, top, top, bottom, bottom);
            }
            triangle(renderer, x + width * 0.5, y + 2 * scale, x + width * 0.78, y + 2 * scale,
                x + width * 0.62, y + height * 0.56, tint(WHITE, alpha * 0.025));
            renderer.quad(x + radius, y + scale, width - radius * 2, scale, tint(WHITE, alpha * 0.14));
        }
    }

    public void accentBar(HudRenderer renderer, double x, double y, double height, double scale, double alpha, Color accent) {
        rounded(renderer, x + 5 * scale, y + height * 0.23, 3 * scale, height * 0.54,
            1.5 * scale, tint(accent, alpha * 0.9));
    }

    public void pill(HudRenderer renderer, double x, double y, double width, double height, double scale,
                     Color fill) {
        rounded(renderer, x, y, width, height, pillRadius(scale), fill);
    }

    public void outline(HudRenderer renderer, double x, double y, double width, double height,
                        double radius, double thickness, Color color) {
        if (width <= 0 || height <= 0 || thickness <= 0) return;
        radius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        thickness = Math.min(thickness, Math.min(width, height) / 2);
        stroke(renderer, x, y, width, height, radius, thickness, color);
    }

    private static void stroke(HudRenderer renderer, double x, double y, double width, double height,
                               double radius, double thickness, Color color) {
        radius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        thickness = Math.min(thickness, Math.min(width, height) / 2);
        renderer.quad(x + radius, y, width - radius * 2, thickness, color);
        renderer.quad(x + radius, y + height - thickness, width - radius * 2, thickness, color);
        renderer.quad(x, y + radius, thickness, height - radius * 2, color);
        renderer.quad(x + width - thickness, y + radius, thickness, height - radius * 2, color);
        double inner = Math.max(0, radius - thickness);
        int segments = GlassCorners.segments(radius);
        for (int corner = 0; corner < 4; corner++) {
            double cx = corner == 0 || corner == 3 ? x + radius : x + width - radius;
            double cy = corner < 2 ? y + radius : y + height - radius;
            for (int i = 0; i < segments; i++) {
                double ax = GlassCorners.x(corner, segments, i);
                double ay = GlassCorners.y(corner, segments, i);
                double bx = GlassCorners.x(corner, segments, i + 1);
                double by = GlassCorners.y(corner, segments, i + 1);
                triangle(renderer, cx + ax * radius, cy + ay * radius, cx + bx * radius, cy + by * radius,
                    cx + bx * inner, cy + by * inner, color);
                triangle(renderer, cx + ax * radius, cy + ay * radius, cx + bx * inner, cy + by * inner,
                    cx + ax * inner, cy + ay * inner, color);
            }
        }
    }

    public Color white(double alpha) {
        return tint(WHITE, alpha);
    }

    public Color tint(Color color, double alpha) {
        set(ink, color, alpha);
        return ink;
    }

    private static void set(Color target, Color source, double alpha) {
        target.set(source.r, source.g, source.b, (int) Math.round(source.a * Math.max(0, Math.min(1, alpha))));
    }

    private static void rounded(HudRenderer renderer, double x, double y, double width, double height,
                         double radius, Color color) {
        if (width <= 0 || height <= 0) return;
        radius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        renderer.quad(x + radius, y, width - radius * 2, height, color);
        renderer.quad(x, y + radius, radius, height - radius * 2, color);
        renderer.quad(x + width - radius, y + radius, radius, height - radius * 2, color);
        int segments = GlassCorners.segments(radius);
        for (int corner = 0; corner < 4; corner++) {
            double cx = corner == 0 || corner == 3 ? x + radius : x + width - radius;
            double cy = corner < 2 ? y + radius : y + height - radius;
            for (int i = 0; i < segments; i++) triangle(renderer, cx, cy,
                cx + GlassCorners.x(corner, segments, i) * radius, cy + GlassCorners.y(corner, segments, i) * radius,
                cx + GlassCorners.x(corner, segments, i + 1) * radius, cy + GlassCorners.y(corner, segments, i + 1) * radius, color);
        }
    }

    private static void triangle(HudRenderer renderer, double x1, double y1, double x2, double y2, double x3, double y3,
                                 Color colour) {
        double cross = (x2 - x1) * (y3 - y1) - (x3 - x1) * (y2 - y1);

        if (cross > 0.0) renderer.triangle(x1, y1, x3, y3, x2, y2, colour);
        else renderer.triangle(x1, y1, x2, y2, x3, y3, colour);
    }
}
