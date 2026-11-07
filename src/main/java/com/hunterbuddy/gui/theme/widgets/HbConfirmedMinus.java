package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedMinus;
import net.minecraft.client.gui.Click;

public class HbConfirmedMinus extends WConfirmedMinus implements HbWidget {
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
    public boolean onMouseReleased(Click click) {
        if (pressed) latch.release();
        return super.onMouseReleased(click);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        HbSignPainter.draw(renderer, theme(), this, false, latch.held(pressed) || pressedOnce,
            pressedOnce ? theme().colors().risk() : theme().minusColor.get());
    }
}
