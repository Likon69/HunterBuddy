package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.widgets.WTopBar;
import meteordevelopment.meteorclient.utils.render.color.Color;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class HbTopBar extends WTopBar implements HbWidget {
    private static final double NOMINAL_HEIGHT = 22;

    @Override
    public void init() {
        for (Tab tab : Tabs.get()) add(new HbTopBarButton(tab));
    }

    @Override
    protected Color getButtonColor(boolean pressed, boolean hovered) {
        return theme().colors().control(pressed, hovered);
    }

    @Override
    protected Color getNameColor() {
        return theme().colors().text();
    }

    private class HbTopBarButton extends WTopBarButton {
        private final Tab tab;

        HbTopBarButton(Tab tab) {
            super(tab);
            this.tab = tab;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            boolean active = mc.currentScreen instanceof TabScreen screen && screen.tab == tab;
            theme().fitted(this, NOMINAL_HEIGHT, () -> style().drawTab(renderer, theme(), this, active, pressed, mouseOver));
            renderer.text(tab.name, x + pad(), y + pad(), style().tabText(theme(), active, mouseOver), false);
        }
    }
}
