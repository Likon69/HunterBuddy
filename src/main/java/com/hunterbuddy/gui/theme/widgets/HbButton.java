package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.widgets.WKeybind;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

public class HbButton extends WButton implements HbWidget {
    public HbButton(String text, GuiTexture texture) {
        super(text, texture);
    }

    @Override
    protected void onCalculateSize() {
        width = HbButtonPainter.width(theme(), getText()) - (parent instanceof WKeybind ? theme.scale(4) : 0);
        height = theme.scale(parent instanceof WKeybind ? 14 : 16);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        HbButtonPainter.draw(renderer, theme(), this, getText(), texture, pressed);
    }
}
