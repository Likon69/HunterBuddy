package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;

import java.util.function.BooleanSupplier;

public class HbLamp extends WWidget implements HbWidget {
    private final BooleanSupplier lit;

    public HbLamp(BooleanSupplier lit) {
        this.lit = lit;
    }

    @Override
    protected void onCalculateSize() {
        width = theme.scale(6);
        height = theme.scale(6);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.quad(this, lit.getAsBoolean() ? theme().accent() : style().palette().dim());
    }
}
