package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedButton;

public class HbConfirmedButton extends WConfirmedButton implements HbWidget {
    public HbConfirmedButton(String text, String confirmText, GuiTexture texture) {
        super(text, confirmText, texture);
    }

    @Override
    protected void onCalculateSize() {
        width = HbButtonPainter.width(theme(), getText());
        height = theme.scale(16);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        HbButtonPainter.draw(renderer, theme(), this, getText(), texture, pressed || pressedOnce);
    }
}
