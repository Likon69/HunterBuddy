package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;

public class HbDropdown<T> extends WDropdown<T> implements HbWidget {
    private final HbPressLatch latch = new HbPressLatch();
    private String[] captions;
    private T cachedValue;
    private String cachedCaption;
    public HbDropdown(T[] values, T value) {
        super(values, value);
    }

    @Override
    public void init() {
        captions = new String[values.length];
        for (int i = 0; i < values.length; i++) captions[i] = values[i].toString();
        super.init();
    }

    private String caption(T value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return captions[i];
        }
        if (cachedValue != value) {
            cachedValue = value;
            cachedCaption = value.toString();
        }
        return cachedCaption;
    }

    @Override
    public double pad() { return theme().clickGui() ? theme().hbScale(2) : super.pad(); }

    public boolean containsPopup(double mouseX, double mouseY) {
        return expanded && root.isOver(mouseX, mouseY);
    }

    @Override
    protected void onCalculateWidgetPositions() {
        root.width = width;
        super.onCalculateWidgetPositions();
        if (theme().clickGui()) tooltip = caption(get());
    }

    @Override
    protected WDropdownRoot createRootWidget() { return new Root(); }

    @Override
    protected WDropdownValue createValueWidget() { return new Value(); }

    @Override
    protected void onCalculateSize() {
        super.onCalculateSize();
        if (!theme().clickGui()) return;
        maxValueWidth = 0;
        for (int i = 0; i < values.length; i++) {
            captions[i] = values[i].toString();
            maxValueWidth = Math.max(maxValueWidth, theme().typography().width(captions[i], 11, false));
        }
        width = maxValueWidth + theme().hbScale(style().dropdownInset() + 18);
        height = theme().hbScale(style().inverted() ? 13 : 15);
        root.width = width;
    }

    @Override
    protected void onPressed(int button) {
        latch.release();
        super.onPressed(button);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (!theme().clickGui()) {
            renderNative(renderer);
            return;
        }
        style().drawDropdownHeader(renderer, theme(), this, expanded, latch.held(pressed), mouseOver);
        theme().typography().draw(renderer, theme().typography().fit(caption(get()), 11, false, Math.max(0, width - theme().hbScale(style().dropdownInset() + 18))), x + theme().hbScale(style().dropdownInset()),
            y + (height - theme().hbScale(11)) / 2, 11, style().dropdownText(theme(), true), false);
    }

    private void renderNative(GuiRenderer renderer) {
        theme().fitted(this, style().inverted() ? 13 : 15, () -> style().drawDropdownBox(renderer, theme(), this, expanded, latch.held(pressed), mouseOver));
        String text = get().toString();
        renderer.text(text, x + pad() + maxValueWidth / 2 - theme().textWidth(text) / 2, y + pad(), style().dropdownText(theme(), true), false);
    }

    private static class Root extends WDropdownRoot implements HbWidget {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            if (theme().clickGui()) return;
            double line = theme().scale(2);
            Color edge = theme().colors().border();
            renderer.quad(x, y + height - line, width, line, edge);
            renderer.quad(x, y, line, height - line, edge);
            renderer.quad(x + width - line, y, line, height - line, edge);
        }
    }

    private class Value extends WDropdownValue implements HbWidget {
        private final HbPressLatch valueLatch = new HbPressLatch();

        @Override
        protected void onPressed(int button) {
            valueLatch.release();
            super.onPressed(button);
        }

        @Override
        protected void onCalculateSize() {
            if (!theme().clickGui()) {
                width = pad() + theme.textWidth(value.toString()) + pad();
                height = pad() + theme.textHeight() + pad();
                return;
            }
            width = theme().typography().width(caption(value), 11, false) + theme().hbScale(12);
            height = theme().hbScale(18);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            boolean selected = value.equals(HbDropdown.this.value);
            theme().fitted(this, 18, () -> style().drawDropdownValue(renderer, theme(), this, selected, valueLatch.held(pressed), mouseOver));
            if (!theme().clickGui()) {
                String text = value.toString();
                renderer.text(text, x + width / 2 - theme.textWidth(text) / 2, y + pad(), style().dropdownText(theme(), selected), false);
                return;
            }
            tooltip = caption(value);
            theme().typography().draw(renderer, theme().typography().fit(caption(value), 11, false, Math.max(0, width - theme().hbScale(12))), x + theme().hbScale(6), y + theme().hbScale(3),
                11, style().dropdownText(theme(), selected), false);
        }
    }
}
