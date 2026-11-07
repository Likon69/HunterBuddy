package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WHorizontalSeparator;

public class HbSeparator extends WHorizontalSeparator implements HbWidget {
    public HbSeparator(String text) {
        super(text);
    }

    @Override
    protected void onCalculateSize() {
        if (!theme().clickGui()) {
            super.onCalculateSize();
            return;
        }
        width = text == null ? 0 : theme().typography().width(text, 11, false) + theme().hbScale(16);
        height = theme().hbScale(text == null ? 7 : 18);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (!theme().clickGui()) {
            renderNative(renderer);
            return;
        }
        double cy = y + height / 2;
        if (text != null) {
            theme().typography().draw(renderer, text, x, y + theme().hbScale(3), 11, theme().separatorText.get(), false);
        } else {
            rule(renderer, x, cy, width, theme().hbScale(1), theme().hbScale(3));
        }
    }

    private void renderNative(GuiRenderer renderer) {
        double line = theme().scale(1);
        double step = theme().scale(3);
        if (text == null) {
            rule(renderer, x, y + height / 2 - line / 2, width, line, step);
            return;
        }
        double gap = theme().scale(2);
        double textStart = Math.round(width / 2.0 - textWidth / 2.0 - gap);
        double textEnd = gap + textStart + textWidth + gap;
        double ruleY = y + Math.round(height / 2.0);
        rule(renderer, x, ruleY, textStart, line, step);
        renderer.text(text, x + textStart + gap, y, theme().separatorText.get(), false);
        rule(renderer, x + textEnd, ruleY, width - textEnd, line, step);
    }

    private void rule(GuiRenderer renderer, double left, double top, double length, double thickness, double step) {
        if (!style().inverted()) {
            renderer.quad(left, top, length, thickness, theme().separatorEdges.get());
            return;
        }
        for (double offset = 0; offset < length; offset += step) {
            renderer.quad(left + offset, top, Math.min(thickness, length - offset), thickness, theme().separatorEdges.get());
        }
    }
}
