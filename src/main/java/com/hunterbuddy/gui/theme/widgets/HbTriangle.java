package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.pressable.WTriangle;

public class HbTriangle extends WTriangle implements HbWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.rotatedQuad(x, y, width, height, rotation, GuiRenderer.TRIANGLE,
            mouseOver ? (style().inverted() ? theme().accent() : style().palette().text()) : style().palette().dim());
    }
}
