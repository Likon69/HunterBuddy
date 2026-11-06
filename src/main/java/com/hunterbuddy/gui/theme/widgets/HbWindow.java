package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import net.minecraft.util.math.MathHelper;

import static meteordevelopment.meteorclient.utils.Utils.getWindowHeight;

public class HbWindow extends WWindow implements HbWidget {
    private final HbCategoryActivity activity;

    public HbWindow(WWidget icon, String title, HbCategoryActivity activity) {
        super(icon, title);
        this.activity = activity;
    }

    private boolean moduleWindow() {
        return activity != null || "search".equals(id) || "favorites".equals(id);
    }

    @Override
    public void init() {
        super.init();
        if (!moduleWindow()) return;
        minWidth = activity != null && activity.name().equals("Hunt") ? (style().hasCounter() || style().inverted() ? 262 : 258) : 228;
        Cell<?> content = cells.getLast();
        content.padTop(style().contentTop()).padBottom(style().contentBottom());
        if (style().inverted()) {
            cells.getFirst().padHorizontal(1).padTop(1);
            content.padHorizontal(1);
        }
        updateNumber();
    }

    private void updateNumber() {
        if (activity == null || id == null || !(parent instanceof WContainer container)) return;
        int number = 0;
        for (int i = 0; i < container.cells.size(); i++) {
            WWidget widget = container.cells.get(i).widget();
            if (widget instanceof HbWindow window && window.activity != null && window.id != null) number++;
            if (widget == this) {
                activity.number(number);
                return;
            }
        }
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new HbHeader(icon);
    }

    @Override
    public void calculateSize() {
        updateNumber();
        if (moduleWindow() && view != null) {
            view.maxHeight = Math.max(theme.scale(22), getWindowHeight() - theme.scale("search".equals(id) ? 148 : 128));
        }
        super.calculateSize();
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (expanded || animProgress > 0) {
            style().drawWindowBody(renderer, theme(), x, y + header.height, width, height - header.height);
        }
    }

    @Override
    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        boolean hidden = super.render(renderer, mouseX, mouseY, delta);
        if (!hidden) {
            double visibleHeight = header.height + (height - header.height) * animProgress;
            style().drawWindowFrame(renderer, theme(), x, y, width, visibleHeight);
        }
        return hidden;
    }

    private class HbHeader extends WHeader {
        private String display;
        private String[] letters;
        private double[] offsets;

        HbHeader(WWidget icon) {
            super(icon);
        }

        @Override
        public void init() {
            if (!moduleWindow()) {
                super.init();
                return;
            }
            display = style().hasCounter() ? String.join(" ", title.toUpperCase(java.util.Locale.ROOT).split("")) : title;
            if (style().hasCounter()) {
                letters = new String[display.length()];
                offsets = new double[display.length()];
                for (int i = 0; i < display.length(); i++) letters[i] = display.substring(i, i + 1);
            }
            if (!style().inverted() && !style().hasCounter()) add(new HbLamp(() -> true));
            if (style().hasCounter() && activity != null) add(new HbCounter(activity));
        }

        @Override
        protected void onCalculateSize() {
            if (!moduleWindow()) {
                super.onCalculateSize();
                return;
            }
            width = theme().typography().width(display, 12, !style().hasCounter()) + theme.scale(80);
            if (style().hasCounter()) {
                double offset = 0;
                for (int i = 0; i < letters.length; i++) {
                    offsets[i] = offset;
                    offset += theme().typography().width(letters[i], 12, false) + theme.scale(3);
                }
                width += theme.scale(Math.max(0, display.length() - 1) * 3);
            }
            height = theme.scale(style().headerHeight());
        }

        @Override
        protected void onCalculateWidgetPositions() {
            if (!moduleWindow()) {
                super.onCalculateWidgetPositions();
                return;
            }
            for (int i = 0; i < cells.size(); i++) {
                Cell<?> cell = cells.get(i);
                WWidget widget = cell.widget();
                cell.x = widget instanceof HbCounter ? x + width - theme.scale(10) - widget.width : x + theme.scale(10);
                cell.y = y + theme.scale(11) - widget.height / 2;
                cell.width = widget.width;
                cell.height = widget.height;
                cell.alignWidget();
            }
        }

        @Override
        public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            if (!moduleWindow()) return super.render(renderer, mouseX, mouseY, delta);
            if (!visible) return true;
            animProgress = MathHelper.clamp(animProgress + (expanded ? 1 : -1) * delta * 14, 0, 1);
            onRender(renderer, mouseX, mouseY, delta);
            for (int i = 0; i < cells.size(); i++) cells.get(i).widget().render(renderer, mouseX, mouseY, delta);
            return false;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            boolean active = activity != null && activity.any();
            double phase = "Visuals".equals(title) ? 2.5 : "Utility".equals(title) ? 4.2 : 0;
            style().drawWindowHeader(renderer, theme(), this, active, phase);
            if (!moduleWindow()) return;
            double textX = x + theme.scale(style().hasCounter() || style().inverted() ? 10 : 24);
            if (style().inverted() && activity != null) {
                String number = activity.number();
                theme().typography().draw(renderer, number, textX, y + theme.scale(5), 12, style().palette().dim(), false);
                textX += theme().typography().width(number, 12, false) + theme.scale(10);
            }
            if (style().hasCounter()) {
                for (int i = 0; i < letters.length; i++) {
                    theme().typography().draw(renderer, letters[i], textX + offsets[i], y + theme.scale(5), 12, style().palette().text(), false);
                }
            } else {
                theme().typography().draw(renderer, title, textX, y + theme.scale(5), 12,
                    style().inverted() ? theme().accent() : style().palette().text(), true);
            }
        }
    }
}
