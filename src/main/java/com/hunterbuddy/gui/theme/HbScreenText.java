package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

final class HbScreenText implements TextRenderer {
    private final HbTypography typography;
    private final HbMetrics metrics;
    private final Color readable = new Color();
    private double alpha = 1;
    private double scale = 1;
    private boolean building;
    private boolean started;

    HbScreenText(HbTypography typography, HbMetrics metrics) {
        this.typography = typography;
        this.metrics = metrics;
    }

    private double ratio() {
        return metrics.meteorTextHeight() / typography.baseHeight(false);
    }

    @Override
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public void begin(double scale, boolean scaleOnly, boolean big) {
        if (building) throw new IllegalStateException("Theme text rendering is already active.");
        this.scale = scale;
        building = true;
        started = false;
    }

    @Override
    public double getWidth(String text, int length, boolean shadow) {
        TextRenderer font = typography.font(false);
        if (started) return font.getWidth(text, length, shadow);
        double width = font.getWidth(text, length, shadow) * ratio();
        return building ? width * scale : width;
    }

    @Override
    public double getHeight(boolean shadow) {
        return metrics.meteorTextHeight() * (building ? scale : 1);
    }

    @Override
    public double render(String text, double x, double y, Color color, boolean shadow) {
        if (text.isEmpty()) return x;
        boolean standalone = !building;
        if (standalone) begin(1, false, false);
        TextRenderer font = typography.font(false);
        if (!started) {
            font.begin(scale * ratio());
            started = true;
        }
        font.setAlpha(alpha);
        double result = font.render(text, x, y, HbDraw.textColor(readable, color), shadow);
        if (standalone) end();
        return result;
    }

    @Override
    public boolean isBuilding() {
        return building;
    }

    @Override
    public void end() {
        if (!building) throw new IllegalStateException("Theme text rendering is not active.");
        if (started) typography.font(false).end();
        started = false;
        building = false;
    }
}
