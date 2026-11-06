package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;

public class HbSlider extends WSlider implements HbWidget {
    public double step;
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
        width = theme().hbScale(style().inverted() ? 120 : 90);
        height = theme().hbScale(style().inverted() ? 18 : 12);
    }

    @Override
    public boolean onMouseScrolled(double amount) {
        if (step == 0) return super.onMouseScrolled(amount);
        if (!handleMouseOver && !scrollHandleMouseOver) return false;
        set(get() + amount * step);
        if (action != null) action.run();
        if (actionOnRelease != null) actionOnRelease.run();
        return true;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        style().drawSlider(renderer, theme(), this, fraction());
    }
}
