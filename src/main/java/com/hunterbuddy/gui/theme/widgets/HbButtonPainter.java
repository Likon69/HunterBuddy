package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.HbGuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class HbButtonPainter {
    private HbButtonPainter() {
    }

    public static double width(HbGuiTheme theme, String text) {
        return text == null ? theme.hbScale(16) : theme.typography().width(text, 10, false) + theme.hbScale(20);
    }

    public static void draw(GuiRenderer renderer, HbGuiTheme theme, WWidget button, String text, double textWidth, GuiTexture texture, boolean pressed) {
        boolean filled = theme.style().buttonFilled("Bind".equals(text), pressed, button.mouseOver);
        theme.fitted(button, 16, () -> theme.style().drawButton(renderer, theme, button, filled, pressed, button.mouseOver));
        Color color = theme.style().buttonText(theme, filled, button.mouseOver);
        if (!theme.clickGui()) {
            drawNative(renderer, theme, button, text, textWidth, texture, color);
        } else if (text != null) {
            String display = theme.typography().fit(text, 10, false, Math.max(0, button.width - theme.hbScale(8)));
            if (!display.equals(text)) button.tooltip = text;
            double width = theme.typography().width(display, 10, false);
            theme.typography().draw(renderer, display, button.x + (button.width - width) / 2,
                button.y + (button.height - theme.hbScale(theme.textSize(10))) / 2, 10, color, false);
        } else if (texture != null) {
            double size = theme.hbScale(10);
            renderer.quad(button.x + (button.width - size) / 2, button.y + (button.height - size) / 2, size, size, texture, color);
        }
    }

    private static void drawNative(GuiRenderer renderer, HbGuiTheme theme, WWidget button, String text, double textWidth, GuiTexture texture, Color color) {
        double pad = button.pad();
        if (text != null) {
            renderer.text(text, button.x + button.width / 2 - textWidth / 2, button.y + pad, color, false);
        } else if (texture != null) {
            double size = theme.textHeight();
            renderer.quad(button.x + button.width / 2 - size / 2, button.y + pad, size, size, texture, color);
        }
    }
}
