package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.gui.widgets.WLabel;

public class HbLabel extends WLabel implements HbWidget {
    private String display = "";

    public HbLabel(String text, boolean title) {
        super(text, title);
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
        if (!theme().clickGui()) return;
        display = theme().typography().fit(text, title ? 13.75 : 11, false, width);
        if (!display.equals(text) && (tooltip == null || !tooltip.startsWith(text))) {
            tooltip = text + (tooltip == null ? "" : "\n" + tooltip);
        }
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Color tint = color != null ? color : title ? theme().titleTextColor.get() : theme().textColor.get();
        if (!theme().clickGui()) {
            if (!text.isEmpty()) renderer.text(text, x, y, tint, title);
            return;
        }
        theme().typography().draw(renderer, display, x, y, title ? 13.75 : 11, tint, false);
    }
}
