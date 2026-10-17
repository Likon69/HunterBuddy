package com.hunterbuddy.modules.messenger.ui;

import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class Painter {
    private final HudRenderer renderer;
    private final double scale;
    private final boolean shadow;
    private double alpha = 1.0;
    private double extra = Double.NaN;

    public Painter(HudRenderer renderer, double scale, boolean shadow) {
        this.renderer = renderer;
        this.scale = scale;
        this.shadow = shadow;
    }

    public boolean hasShadow() {
        return shadow;
    }

    public void alpha(double alpha) {
        this.alpha = alpha;
    }

    public double lineHeight() {
        return renderer.textHeight(shadow, scale);
    }

    public double width(String text) {
        return renderer.textWidth(text, shadow, scale);
    }

    public double advance(String text) {
        if (text.isEmpty()) return 0.0;
        if (Double.isNaN(extra)) extra = 2.0 * width("x") - width("xx");
        return width(text) - extra;
    }

    public Color tint(Color color) {
        if (alpha >= 1.0) return color;
        return new Color(color.r, color.g, color.b, (int) (color.a * alpha));
    }

    public Color with(Color color, int a) {
        return new Color(color.r, color.g, color.b, (int) (a * alpha));
    }

    public void text(String text, double x, double y, Color color) {
        renderer.text(text, x, y, tint(color), shadow, scale);
    }

    public void rect(double x, double y, double w, double h, Color color) {
        renderer.quad(x, y, w, h, tint(color));
    }

    public void round(double x, double y, double w, double h, double radius, Color color) {
        round(x, y, w, h, radius, color, true, true);
    }

    public void round(double x, double y, double w, double h, double radius, Color color, boolean top, boolean bottom) {
        Color c = tint(color);
        double r = Math.min(radius, Math.min(w, h) / 2.0);
        int steps = (int) Math.ceil(r);
        int topRows = top ? steps : 0;
        int bottomRows = bottom ? steps : 0;

        double midH = h - topRows - bottomRows;
        if (midH > 0) renderer.quad(x, y + topRows, w, midH, c);

        for (int i = 0; i < steps; i++) {
            double dy = r - (i + 0.5);
            double inset = r - Math.sqrt(Math.max(0.0, r * r - dy * dy));
            if (top) renderer.quad(x + inset, y + i, w - inset * 2.0, 1.0, c);
            if (bottom) renderer.quad(x + inset, y + h - 1.0 - i, w - inset * 2.0, 1.0, c);
        }
    }

    public void bordered(double x, double y, double w, double h, double radius, Color fill, Color border) {
        round(x + 1.0, y + 1.0, w - 2.0, h - 2.0, Math.max(0.0, radius - 1.0), fill);
        outline(x, y, w, h, radius, border);
    }

    private void outline(double x, double y, double w, double h, double radius, Color color) {
        Color c = tint(color);
        double r = Math.min(radius, Math.min(w, h) / 2.0);
        int steps = (int) Math.ceil(r);

        double edge = cornerInset(r, 0);
        renderer.quad(x + edge, y, w - edge * 2.0, 1.0, c);
        renderer.quad(x + edge, y + h - 1.0, w - edge * 2.0, 1.0, c);

        for (int i = 1; i < steps; i++) {
            double outer = cornerInset(r, i);
            double bw = Math.max(1.0, 1.0 + cornerInset(r - 1.0, i - 1) - outer);
            renderer.quad(x + outer, y + i, bw, 1.0, c);
            renderer.quad(x + w - outer - bw, y + i, bw, 1.0, c);
            renderer.quad(x + outer, y + h - 1.0 - i, bw, 1.0, c);
            renderer.quad(x + w - outer - bw, y + h - 1.0 - i, bw, 1.0, c);
        }

        double side = h - steps * 2.0;
        if (side > 0.0) {
            renderer.quad(x, y + steps, 1.0, side, c);
            renderer.quad(x + w - 1.0, y + steps, 1.0, side, c);
        }
    }

    private static double cornerInset(double radius, int row) {
        double dy = radius - (row + 0.5);
        return radius - Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
    }

    public void gradient(double x, double y, double w, double h, Color top, Color bottom) {
        Color a = tint(top);
        Color b = tint(bottom);
        renderer.quad(x, y, w, h, a, a, b, b);
    }

    public void shadow(double x, double y, double w, double h, double radius, int rings, int alpha) {
        Color shade = tint(new Color(0, 0, 0, alpha));
        for (int ring = 1; ring <= rings; ring++) frame(x, y + 2.0, w, h, radius, ring, shade);
    }

    private void frame(double x, double y, double w, double h, double radius, int grow, Color c) {
        double r = Math.min(radius, Math.min(w, h) / 2.0) + grow;
        int rows = (int) Math.ceil(r);
        double left = x - grow;
        double right = x + w + grow;

        for (int k = 0; k < rows; k++) {
            double outer = cornerInset(r, k);
            double top = y - grow + k;
            double bottom = y + h + grow - 1.0 - k;

            if (k == 0) {
                renderer.quad(left + outer, top, right - left - outer * 2.0, 1.0, c);
                renderer.quad(left + outer, bottom, right - left - outer * 2.0, 1.0, c);
                continue;
            }

            double piece = Math.max(1.0, 1.0 + cornerInset(r - 1.0, k - 1) - outer);
            renderer.quad(left + outer, top, piece, 1.0, c);
            renderer.quad(right - outer - piece, top, piece, 1.0, c);
            renderer.quad(left + outer, bottom, piece, 1.0, c);
            renderer.quad(right - outer - piece, bottom, piece, 1.0, c);
        }

        double middle = h + grow * 2.0 - rows * 2.0;
        if (middle > 0.0) {
            renderer.quad(left, y - grow + rows, 1.0, middle, c);
            renderer.quad(right - 1.0, y - grow + rows, 1.0, middle, c);
        }
    }

    public String fit(String text, double maxWidth) {
        if (width(text) <= maxWidth) return text;

        String cut = text;
        while (cut.length() > 1 && width(cut + "...") > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }
}
