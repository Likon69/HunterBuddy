package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;

public class HbCheckbox extends WCheckbox implements HbWidget {
    public HbCheckbox(boolean checked) {
        super(checked);
    }

    @Override
    protected void onCalculateSize() {
        width = theme.scale(10);
        height = theme.scale(10);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        style().drawCheckbox(renderer, theme(), this, checked, mouseOver);
    }
}
