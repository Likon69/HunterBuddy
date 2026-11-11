package com.hunterbuddy.hud.system;

import meteordevelopment.meteorclient.utils.render.color.Color;

public final class SystemStatsRenderProbe {
    public static final class Value {
        private final Object value;
        public Value(Object value) { this.value = value; }
        public Object get() { return value; }
    }

    boolean checking;
    double left;
    double top;
    double right;
    double bottom;
    double firstGraph;
    double secondGraph;
    double graphHeight;
    double graphRight;
    double graphLeft;
    int triangles;
    long commands;
    double checksum;

    public double textWidth(String value, boolean shadow, double scale) {
        double width = 0;
        for (int i = 0; i < value.length(); i++) width += value.charAt(i) == '8' ? 10 : 9;
        return width * scale + (shadow ? 1 : 0);
    }

    public double textHeight(boolean shadow, double scale) {
        return 18 * scale + (shadow ? 1 : 0);
    }

    public double text(String value, double x, double y, Color color, boolean shadow, double scale) {
        double width = textWidth(value, shadow, scale);
        if (checking) {
            inside(x, y);
            inside(x + width, y + textHeight(shadow, scale));
        }
        commands++;
        checksum += x + y + width + color.a;
        return x + width;
    }

    public void quad(double x, double y, double width, double height, Color color) {
        if (checking) {
            if (width < 0 || height < 0) throw new AssertionError("Negative rectangle");
            inside(x, y);
            inside(x + width, y + height);
        }
        commands++;
        checksum += x + y + width + height + color.a;
    }

    public void quad(double x, double y, double width, double height, Color tl, Color tr, Color br, Color bl) {
        quad(x, y, width, height, tl);
    }

    public void triangle(double ax, double ay, double bx, double by, double cx, double cy, Color color) {
        if (checking) {
            graph(ax, ay);
            graph(bx, by);
            graph(cx, cy);
        }
        triangles++;
        commands++;
        checksum += ax + ay + bx + by + cx + cy + color.a;
    }

    private void inside(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < left - 0.000001 || x > right + 0.000001
            || y < top - 0.000001 || y > bottom + 0.000001) throw new AssertionError("Drawing outside content");
    }

    private void graph(double x, double y) {
        inside(x, y);
        if (x < graphLeft || x > graphRight || !(y >= firstGraph && y <= firstGraph + graphHeight
            || y >= secondGraph && y <= secondGraph + graphHeight)) throw new AssertionError("Drawing outside graph");
    }
}
