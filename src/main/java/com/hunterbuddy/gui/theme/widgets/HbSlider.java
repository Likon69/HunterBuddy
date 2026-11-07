package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;

public class HbSlider extends WSlider implements HbWidget {
    public HbSlider(double value, double min, double max) {
        super(value, min, max);
    }

    @Override
    protected double handleSize() {
        return 0;
    }

    public double fraction() {
        return max == min ? 0 : Math.clamp((get() - min) / (max - min), 0, 1);
    }

    @Override
    protected double valueWidth() {
        return fraction() * width;
    }

    @Override
    protected void onCalculateSize() {
        if (!theme().clickGui()) {
            width = theme().textHeight();
            height = width;
            return;
        }
        width = theme().hbScale(style().inverted() ? 120 : 90);
        height = theme().hbScale(style().inverted() ? 18 : 12);
    }

    @Override
    public boolean onMouseScrolled(double amount) {
        return !theme().clickGui() && super.onMouseScrolled(amount);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        theme().fitted(this, style().inverted() ? 18 : 12, () -> style().drawSlider(renderer, theme(), this, fraction()));
    }
}
