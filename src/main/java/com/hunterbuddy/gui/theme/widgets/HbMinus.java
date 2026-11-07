package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;

public class HbMinus extends WMinus implements HbWidget {
    private final HbPressLatch latch = new HbPressLatch();

    @Override
    protected void onCalculateSize() {
        if (!theme().clickGui()) {
            super.onCalculateSize();
            return;
        }
        width = HbSignPainter.side(theme());
        height = width;
    }

    @Override
    protected void onPressed(int button) {
        latch.release();
        super.onPressed(button);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        HbSignPainter.draw(renderer, theme(), this, false, latch.held(pressed), theme().minusColor.get());
    }
}
