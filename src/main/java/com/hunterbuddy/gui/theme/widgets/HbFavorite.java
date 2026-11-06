package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.pressable.WFavorite;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class HbFavorite extends WFavorite implements HbWidget {
    public HbFavorite(boolean checked) {
        super(checked);
    }

    @Override
    protected void onCalculateSize() {
        width = theme.scale(10);
        height = theme.scale(10);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        style().drawFavorite(renderer, theme(), this, checked);
    }

    @Override
    protected Color getColor() {
        return theme().accent();
    }
}
