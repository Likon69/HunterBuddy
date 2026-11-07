package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;

public class HbCheckbox extends WCheckbox implements HbWidget {
    private final HbPressLatch latch = new HbPressLatch();

    public HbCheckbox(boolean checked) {
        super(checked);
    }

    @Override
    protected void onCalculateSize() {
        if (!theme().clickGui()) {
            super.onCalculateSize();
            return;
        }
        width = theme().hbScale(10);
        height = theme().hbScale(10);
    }

    @Override
    protected void onPressed(int button) {
        latch.release();
        super.onPressed(button);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        theme().fitted(this, 10, () -> style().drawCheckbox(renderer, theme(), this, checked, latch.held(pressed), mouseOver));
    }
}
