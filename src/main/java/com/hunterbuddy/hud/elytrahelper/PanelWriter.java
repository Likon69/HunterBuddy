package com.hunterbuddy.hud.elytrahelper;

import com.hunterbuddy.hud.text.HudText;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class PanelWriter {
    private static final double LINE_SPACING = 2.0;

    private final HudRenderer renderer;
    private final double originY;
    private final double left;
    private final boolean shadow;
    private final double scale;
    private final Color labelColor;
    private final Color valueColor;
    private double cursorY;
    private double widest;

    public PanelWriter(HudRenderer renderer, double left, double top, TextStyle style) {
        this.renderer = renderer;
        this.left = left;
        this.originY = top;
        this.cursorY = top;
        this.shadow = style.shadow();
        this.scale = style.scale();
        this.labelColor = style.labelColor();
        this.valueColor = style.valueColor();
    }

    public void line(String text, Color color) {
        HudText.text(renderer, text, left, cursorY, color, shadow, scale);
        widest = Math.max(widest, HudText.textWidth(renderer, text, shadow, scale));
        advance();
    }

    public void pair(String label, String value) {
        double labelWidth = HudText.textWidth(renderer, label, shadow, scale);
        HudText.text(renderer, label, left, cursorY, labelColor, shadow, scale);
        HudText.text(renderer, value, left + labelWidth, cursorY, valueColor, shadow, scale);
        widest = Math.max(widest, labelWidth + HudText.textWidth(renderer, value, shadow, scale));
        advance();
    }

    public double widest() {
        return widest;
    }

    public double height() {
        return cursorY - originY;
    }

    private void advance() {
        cursorY += HudText.textHeight(renderer, shadow, scale) + LINE_SPACING;
    }
}
