package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;

public class HbCounter extends WWidget implements HbWidget {
    private final HbCategoryActivity activity;

    public HbCounter(HbCategoryActivity activity) {
        this.activity = activity;
    }

    @Override
    protected void onCalculateSize() {
        width = theme().typography().width(activity.capacity(), 11, false);
        height = theme.scale(11);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        String text = activity.counter();
        double textWidth = theme().typography().width(text, 11, false);
        theme().typography().draw(renderer, text, x + width - textWidth, y, 11, style().palette().dim(), false);
    }
}
