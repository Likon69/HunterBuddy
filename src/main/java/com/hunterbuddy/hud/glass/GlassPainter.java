package com.hunterbuddy.hud.glass;

import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class GlassPainter {
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color BLACK = new Color(0, 0, 0);
    private final Color ink = new Color();
    private final Color top = new Color();
    private final Color bottom = new Color();

    public static double radius(double scale) {
        return 9 * scale;
    }

    public static double centered(HudRenderer renderer, double center, double textScale) {
        return center - renderer.textHeight(false, textScale) / 2;
    }

    public static String fit(HudRenderer renderer, String value, double available, double scale) {
        if (renderer.textWidth(value, false, scale) <= available) return value;
        int end = value.length();
        while (end > 0) {
            end = value.offsetByCodePoints(end, -1);
            String shortened = value.substring(0, end) + "...";
            if (renderer.textWidth(shortened, false, scale) <= available) return shortened;
        }
        return "...";
    }

    public void card(HudRenderer renderer, double x, double y, double width, double height, double scale,
                     double alpha, boolean reflection, Color background) {
        double radius = radius(scale);
        for (int layer = 3; layer >= 1; layer--) {
            double pad = layer * 2 * scale;
            rounded(renderer, x - pad, y - pad + 2 * scale, width + pad * 2, height + pad * 2,
                radius + pad, tint(BLACK, alpha * 0.07));
        }
        rounded(renderer, x, y, width, height, radius, tint(WHITE, alpha * 0.16));
        rounded(renderer, x + scale, y + scale, width - 2 * scale, height - 2 * scale,
            radius - scale, tint(background, alpha));
        if (reflection) {
            set(top, WHITE, alpha * 0.09);
            set(bottom, WHITE, 0);
            renderer.quad(x + radius, y + scale, width - radius * 2, height * 0.5, top, top, bottom, bottom);
            renderer.triangle(x + width * 0.5, y + 2 * scale, x + width * 0.78, y + 2 * scale,
                x + width * 0.62, y + height * 0.56, tint(WHITE, alpha * 0.025));
            renderer.quad(x + radius, y + scale, width - radius * 2, scale, tint(WHITE, alpha * 0.25));
        }
    }

    public void accentBar(HudRenderer renderer, double x, double y, double height, double scale, double alpha,
                          Color accent) {
        rounded(renderer, x + 2 * scale, y + height * 0.23, 3 * scale, height * 0.54,
            1.5 * scale, tint(accent, alpha * 0.9));
    }

    public void pill(HudRenderer renderer, double x, double y, double width, double height, double scale,
                     Color fill) {
        rounded(renderer, x, y, width, height, 5 * scale, fill);
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
        renderer.quad(x + radius, y, width - radius * 2, height, color);
        renderer.quad(x, y + radius, radius, height - radius * 2, color);
        renderer.quad(x + width - radius, y + radius, radius, height - radius * 2, color);
        for (int corner = 0; corner < 4; corner++) {
            double cx = corner == 0 || corner == 3 ? x + radius : x + width - radius;
            double cy = corner < 2 ? y + radius : y + height - radius;
            double start = (corner + 2) * Math.PI / 2;
            for (int i = 0; i < 5; i++) {
                double a = start + i * Math.PI / 10;
                double b = a + Math.PI / 10;
                renderer.triangle(cx, cy, cx + Math.cos(a) * radius, cy + Math.sin(a) * radius,
                    cx + Math.cos(b) * radius, cy + Math.sin(b) * radius, color);
            }
        }
    }
}
