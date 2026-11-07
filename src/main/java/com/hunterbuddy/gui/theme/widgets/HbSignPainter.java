package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.HbGuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

final class HbSignPainter {
    private static final double SIDE = 16;

    private HbSignPainter() {
    }

    static double side(HbGuiTheme theme) {
        return theme.hbScale(SIDE);
    }

    static void draw(GuiRenderer renderer, HbGuiTheme theme, WWidget button, boolean plus, boolean pressed, Color glyph) {
        theme.fitted(button, SIDE, () -> {
            boolean filled = theme.style().buttonFilled(false, pressed, button.mouseOver);
            theme.style().drawButton(renderer, theme, button, filled, pressed, button.mouseOver);
            Color color = filled ? theme.colors().onAccent() : glyph;
            double length = theme.hbScale(8);
            double thickness = theme.hbScale(2);
            double cx = button.x + button.width / 2;
            double cy = button.y + button.height / 2;
            renderer.quad(cx - length / 2, cy - thickness / 2, length, thickness, color);
            if (plus) renderer.quad(cx - thickness / 2, cy - length / 2, thickness, length, color);
        });
    }
}
