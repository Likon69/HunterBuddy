package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class HbDraw {
    private HbDraw() {
    }

    public static Color withAlpha(Color color, double factor) {
        return new Color(color.r, color.g, color.b, (int) Math.round(color.a * factor));
    }

    public static Color withAlpha(Color target, Color color, double factor) {
        target.set(color);
        target.a = (int) Math.round(color.a * factor);
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

    public static void chamferedTop(GuiRenderer renderer, double x, double y, double width, double height, double chamfer, Color color) {
        renderer.quad(x + chamfer, y, width - chamfer * 2, chamfer, color);
        renderer.triangle(x + chamfer, y, x, y + chamfer, x + chamfer, y + chamfer, color);
        renderer.triangle(x + width - chamfer, y, x + width - chamfer, y + chamfer, x + width, y + chamfer, color);
        renderer.quad(x, y + chamfer, width, height - chamfer, color);
    }

    public static void arrow(GuiRenderer renderer, double cx, double cy, double halfWidth, double halfHeight, boolean up, Color color) {
        if (up) {
            renderer.triangle(cx - halfWidth, cy + halfHeight, cx + halfWidth, cy + halfHeight, cx, cy - halfHeight, color);
        } else {
            renderer.triangle(cx - halfWidth, cy - halfHeight, cx, cy + halfHeight, cx + halfWidth, cy - halfHeight, color);
        }
    }
}
