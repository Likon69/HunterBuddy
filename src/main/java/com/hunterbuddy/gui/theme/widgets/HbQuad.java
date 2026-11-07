package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.HbDraw;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WQuad;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class HbQuad extends WQuad implements HbWidget {
    private static final double SIDE = 16;

    public HbQuad(Color color) {
        super(color);
    }

    @Override
    protected void onCalculateSize() {
        if (!theme().clickGui()) {
            super.onCalculateSize();
            return;
        }
        width = theme().hbScale(SIDE);
        height = width;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.quad(x, y, width, height, color);
        theme().fitted(this, SIDE, () -> HbDraw.border(renderer, x, y, width, height, theme().hbScale(1), theme().colors().border()));
    }
}
