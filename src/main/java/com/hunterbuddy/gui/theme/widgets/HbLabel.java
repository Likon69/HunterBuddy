package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WLabel;

public class HbLabel extends WLabel implements HbWidget {
    public HbLabel(String text, boolean title) {
        super(text, title);
    }

    @Override
    protected void onCalculateWidgetPositions() {
        String display = theme().typography().fit(text, title ? 13.75 : 11, false, width);
        if (!display.equals(text) && (tooltip == null || !tooltip.startsWith(text))) {
            tooltip = text + (tooltip == null ? "" : "\n" + tooltip);
        }
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        theme().typography().draw(renderer, theme().typography().fit(text, title ? 13.75 : 11, false, width), x, y, title ? 13.75 : 11,
            color != null ? color : title ? theme().titleTextColor.get() : theme().textColor.get(), false);
    }
}
