package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WLabel;

public class HbLabel extends WLabel implements HbWidget {
    public HbLabel(String text, boolean title) {
        super(text, title);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        theme().typography().draw(renderer, text, x, y, title ? 13.75 : 11,
            color != null ? color : title ? theme().titleTextColor.get() : theme().textColor.get(), false);
    }
}
