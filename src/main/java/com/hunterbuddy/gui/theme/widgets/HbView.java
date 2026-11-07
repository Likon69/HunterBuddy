package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;

public class HbView extends WView implements HbWidget {
    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (!canScroll || !hasScrollBar) return;
        double inset = theme().hbScale(1);
        renderer.quad(handleX() + inset, handleY(), handleWidth() - inset * 2, handleHeight(),
            theme().scrollbarColor.get(focused, handleMouseOver));
    }
}
