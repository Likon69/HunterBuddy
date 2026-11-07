package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.HbDraw;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WTooltip;

public class HbTooltip extends WTooltip implements HbWidget {
    public HbTooltip(String text) {
        super(text);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.quad(this, theme().colors().window());
        HbDraw.border(renderer, x, y, width, height, theme().hbScale(1), theme().colors().border());
    }
}
