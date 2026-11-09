package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.HbDraw;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;

public class HbSection extends WSection implements HbWidget {
    public HbSection(String title, boolean expanded, WWidget headerWidget) {
        super(title, expanded, headerWidget);
    }

    @Override
    protected WHeader createHeader() {
        return new HbSectionHeader(title);
    }

    private class HbSectionHeader extends WHeader {
        HbSectionHeader(String title) {
            super(title);
        }

        @Override
        public void init() {
            if (headerWidget != null) add(headerWidget).centerY();
        }

        @Override
        protected void onCalculateSize() {
            super.onCalculateSize();
            width += theme().typography().width(title, 11, true) + theme().hbScale(42);
            height = Math.max(height + theme().hbScale(8), theme().hbScale(26));
        }

        @Override
        protected void onCalculateWidgetPositions() {
            for (Cell<?> cell : cells) {
                cell.x = x + width - cell.widget().width - theme().hbScale(8);
                cell.y = y;
                cell.width = cell.widget().width;
                cell.height = height;
                cell.alignWidget();
            }
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            style().drawSectionHeader(renderer, theme(), this, mouseOver);
            HbDraw.chevron(renderer, x + theme().hbScale(14), y + height / 2,
                theme().hbScale(5), theme().hbScale(1.8), animProgress, theme().accent());
            theme().typography().draw(renderer, title, x + theme().hbScale(28),
                y + (height - theme().hbScale(theme().textSize(11))) / 2, 11, theme().colors().text(), true);
        }
    }
}
