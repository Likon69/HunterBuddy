package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

final class HbReadableText implements TextRenderer {
    private final Color readable = new Color();

    @Override
    public void setAlpha(double alpha) {
        TextRenderer.get().setAlpha(alpha);
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big) {
        TextRenderer.get().begin(scale, scaleOnly, big);
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        return TextRenderer.get().getWidth(text, length, shadow);
    }

    @Override
    public double getHeight(boolean shadow) {
        return TextRenderer.get().getHeight(shadow);
    }

    @Override
    public double render(String text, double x, double y, Color color, boolean shadow) {
        return TextRenderer.get().render(text, x, y, HbDraw.textColor(readable, color), shadow);
    }

    @Override
    public boolean isBuilding() {
        return TextRenderer.get().isBuilding();
    }

    @Override
    public void end() {
        TextRenderer.get().end();
    }
}
