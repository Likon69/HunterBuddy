package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class HbDraw {
    private HbDraw() {
    }

    public static Color withAlpha(Color color, double factor) {
        return new Color(color.r, color.g, color.b, (int) Math.round(color.a * Math.clamp(factor, 0, 1)));
    }

    public static Color withAlpha(Color target, Color color, double factor) {
        target.set(color);
        target.a = (int) Math.round(color.a * Math.clamp(factor, 0, 1));
        return target;
    }

    public static Color textColor(Color target, Color color) {
        target.set(color);
        target.a = Math.max(160, color.a);
        return target;
    }

    public static void diamond(GuiRenderer renderer, double cx, double cy, double radius, Color color) {
        renderer.triangle(cx, cy - radius, cx - radius, cy, cx + radius, cy, color);
        renderer.triangle(cx - radius, cy, cx, cy + radius, cx + radius, cy, color);
    }

    public static void border(GuiRenderer renderer, double x, double y, double width, double height, double thickness, Color color) {
        renderer.quad(x, y, width, thickness, color);
        renderer.quad(x, y + height - thickness, width, thickness, color);
        renderer.quad(x, y + thickness, thickness, height - thickness * 2, color);
        renderer.quad(x + width - thickness, y + thickness, thickness, height - thickness * 2, color);
    }

    public static void bracket(GuiRenderer renderer, double x, double y, double width, double height, double thickness, double serif, Color color) {
        renderer.quad(x, y, thickness, height, color);
        renderer.quad(x, y, serif, thickness, color);
        renderer.quad(x, y + height - thickness, serif, thickness, color);
        renderer.quad(x + width - thickness, y, thickness, height, color);
        renderer.quad(x + width - serif, y, serif, thickness, color);
        renderer.quad(x + width - serif, y + height - thickness, serif, thickness, color);
    }

    public static void chamferedTop(GuiRenderer renderer, double x, double y, double width, double height, double chamfer, Color color) {
        renderer.quad(x + chamfer, y, width - chamfer * 2, chamfer, color);
        renderer.triangle(x + chamfer, y, x, y + chamfer, x + chamfer, y + chamfer, color);
        renderer.triangle(x + width - chamfer, y, x + width - chamfer, y + chamfer, x + width, y + chamfer, color);
        renderer.quad(x, y + chamfer, width, height - chamfer, color);
    }

    public static void chevron(GuiRenderer renderer, double cx, double cy, double radius, double thickness, double progress, Color color) {
        double angle = Math.clamp(progress, 0, 1) * Math.PI / 2;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double ax = -radius / 2;
        double bx = radius / 2;
        double by = 0;
        for (int side = -1; side <= 1; side += 2) {
            double ay = side * radius;
            double dx = bx - ax;
            double dy = by - ay;
            double length = Math.hypot(dx, dy);
            double nx = -dy / length * thickness / 2;
            double ny = dx / length * thickness / 2;
            double x1 = cx + (ax + nx) * cos - (ay + ny) * sin;
            double y1 = cy + (ax + nx) * sin + (ay + ny) * cos;
            double x2 = cx + (bx + nx) * cos - (by + ny) * sin;
            double y2 = cy + (bx + nx) * sin + (by + ny) * cos;
            double x3 = cx + (bx - nx) * cos - (by - ny) * sin;
            double y3 = cy + (bx - nx) * sin + (by - ny) * cos;
            double x4 = cx + (ax - nx) * cos - (ay - ny) * sin;
            double y4 = cy + (ax - nx) * sin + (ay - ny) * cos;
            renderer.triangle(x1, y1, x2, y2, x3, y3, color);
            renderer.triangle(x1, y1, x3, y3, x4, y4, color);
        }
    }

    public static void arrow(GuiRenderer renderer, double cx, double cy, double halfWidth, double halfHeight, boolean up, Color color) {
        if (up) {
            renderer.triangle(cx - halfWidth, cy + halfHeight, cx + halfWidth, cy + halfHeight, cx, cy - halfHeight, color);
        } else {
            renderer.triangle(cx - halfWidth, cy - halfHeight, cx, cy + halfHeight, cx + halfWidth, cy - halfHeight, color);
        }
    }
}
