package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.pressable.WTriangle;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class HbTriangle extends WTriangle implements HbWidget {
    private final HbPressLatch latch = new HbPressLatch();

    @Override
    protected void onPressed(int button) {
        latch.release();
        super.onPressed(button);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        Color color = latch.held(pressed) ? theme().accent()
            : mouseOver ? (style().inverted() ? theme().accent() : theme().colors().text()) : theme().colors().dim();
        renderer.rotatedQuad(x, y, width, height, rotation, GuiRenderer.TRIANGLE, color);
    }
}
