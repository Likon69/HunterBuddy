package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WHorizontalSeparator;

public class HbSeparator extends WHorizontalSeparator implements HbWidget {
    public HbSeparator(String text) {
        super(text);
    }

    @Override
    protected void onCalculateSize() {
        width = text == null ? 0 : theme().typography().width(text, 11, false) + theme.scale(16);
        height = theme.scale(text == null ? 7 : 18);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        double cy = y + height / 2;
        if (text != null) {
            theme().typography().draw(renderer, text, x, y + theme.scale(3), 11, style().palette().dim(), false);
        } else if (style().inverted()) {
            for (double left = x; left < x + width; left += theme.scale(3)) {
                renderer.quad(left, cy, Math.min(theme.scale(1), x + width - left), theme.scale(1), style().palette().border());
            }
        } else {
            renderer.quad(x, cy, width, theme.scale(1), style().palette().border());
        }
    }
}
