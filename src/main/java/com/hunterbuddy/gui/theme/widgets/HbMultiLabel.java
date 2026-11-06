package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WMultiLabel;

public class HbMultiLabel extends WMultiLabel implements HbWidget {
    public HbMultiLabel(String text, boolean title, double maxWidth) {
        super(text, title, maxWidth);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double lineHeight = theme.textHeight(title);
        for (int i = 0; i < lines.size(); i++) {
            theme().typography().draw(renderer, lines.get(i), x, y + lineHeight * i, 11,
                color != null ? color : theme().textColor.get(), false);
        }
    }
}
