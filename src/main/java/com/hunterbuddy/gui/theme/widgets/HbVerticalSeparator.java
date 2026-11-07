package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WVerticalSeparator;

public class HbVerticalSeparator extends WVerticalSeparator implements HbWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double thickness = theme().hbScale(1);
        double left = x + Math.round(width / 2.0);
        if (!style().inverted()) {
            renderer.quad(left, y, thickness, height, theme().separatorEdges.get());
            return;
        }
        for (double offset = 0; offset < height; offset += thickness * 3) {
            renderer.quad(left, y + offset, thickness, Math.min(thickness, height - offset), theme().separatorEdges.get());
        }
    }
}
