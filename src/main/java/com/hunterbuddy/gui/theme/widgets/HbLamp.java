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
        width = theme().hbScale(6);
        height = theme().hbScale(6);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        renderer.quad(this, lit.getAsBoolean() ? theme().accent() : theme().colors().dim());
    }
}
