package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.gui.widgets.WMultiLabel;

public class HbMultiLabel extends WMultiLabel implements HbWidget {

    public HbMultiLabel(String text, boolean title, double maxWidth) {
        super(text, title, maxWidth);
    }

    public boolean rewrap(double pixels) {
        if (pixels <= 0) return false;
        double units = pixels / theme().hbScale(1);
        if (Math.abs(units - maxWidth) <= 0.5) return false;
        maxWidth = units;
        return true;
    }

    @Override
    protected void onCalculateSize() {
        super.onCalculateSize();
        if (!theme().clickGui()) return;
        width = theme().layoutUnits(width);
        height = theme().layoutUnits(height);
    }

    @Override
    protected void onCalculateWidgetPositions() {
        if (theme().clickGui()) tooltip = text;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (!theme().clickGui()) {
            double lineHeight = theme().textHeight(title);
            Color tint = color != null ? color : theme().textColor.get();
            for (int i = 0; i < lines.size(); i++) renderer.text(lines.get(i), x, y + lineHeight * i, tint, false);
            return;
        }
        double lineHeight = theme().hbScale(theme().textSize(title ? 13.75 : 11));
        for (int i = 0; i < lines.size(); i++) {
            theme().typography().draw(renderer, lines.get(i), x, y + lineHeight * i, 11,
                color != null ? color : theme().textColor.get(), false);
        }
    }
}
