package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedButton;
import net.minecraft.client.gui.Click;

public class HbConfirmedButton extends WConfirmedButton implements HbWidget {
    private final HbPressLatch latch = new HbPressLatch();

    public HbConfirmedButton(String text, String confirmText, GuiTexture texture) {
        super(text, confirmText, texture);
    }

    @Override
    protected void onCalculateSize() {
        if (!theme().clickGui()) {
            super.onCalculateSize();
            return;
        }
        width = HbButtonPainter.width(theme(), getText());
        height = theme().hbScale(16);
    }

    @Override
    public boolean onMouseReleased(Click click) {
        if (pressed) latch.release();
        return super.onMouseReleased(click);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        HbButtonPainter.draw(renderer, theme(), this, getText(), textWidth, texture, latch.held(pressed) || pressedOnce);
    }
}
