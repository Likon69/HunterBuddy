package com.hunterbuddy.hud.text;

import meteordevelopment.meteorclient.renderer.text.VanillaTextRenderer;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class HudText {
    private static boolean overlay;

    private HudText() { }

    static void overlay(boolean active) { overlay = active; }

    private static double scale(double requested) {
        double scale = HudTextScale.fixed(requested);
        if (overlay) VanillaTextRenderer.INSTANCE.scale = scale * 2;
        return scale;
    }

    public static double text(HudRenderer renderer, String value, double x, double y, Color color, boolean shadow, double requested) {
        double scale = scale(requested);
        return overlay ? VanillaTextRenderer.INSTANCE.render(value, x, y, color, shadow)
            : renderer.text(value, x, y, color, shadow, scale);
    }

    public static double text(HudRenderer renderer, String value, double x, double y, Color color, boolean shadow) {
        return text(renderer, value, x, y, color, shadow, HudTextScale.NORMAL);
    }

    public static double textWidth(HudRenderer renderer, String value, boolean shadow, double requested) {
        double scale = scale(requested);
        return overlay ? VanillaTextRenderer.INSTANCE.getWidth(value, shadow) : renderer.textWidth(value, shadow, scale);
    }

    public static double textWidth(HudRenderer renderer, String value, double requested) {
        return textWidth(renderer, value, false, requested);
    }

    public static double textWidth(HudRenderer renderer, String value, boolean shadow) {
        return textWidth(renderer, value, shadow, HudTextScale.NORMAL);
    }

    public static double textWidth(HudRenderer renderer, String value) {
        return textWidth(renderer, value, false, HudTextScale.NORMAL);
    }

    public static double textHeight(HudRenderer renderer, boolean shadow, double requested) {
        double scale = scale(requested);
        return overlay ? VanillaTextRenderer.INSTANCE.getHeight(shadow) : renderer.textHeight(shadow, scale);
    }

    public static double textHeight(HudRenderer renderer, boolean shadow) {
        return textHeight(renderer, shadow, HudTextScale.NORMAL);
    }

    public static double textHeight(HudRenderer renderer) {
        return textHeight(renderer, false, HudTextScale.NORMAL);
    }
}
