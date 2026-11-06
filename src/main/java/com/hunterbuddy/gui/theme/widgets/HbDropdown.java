package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;

public class HbDropdown<T> extends WDropdown<T> implements HbWidget {
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
    public double pad() { return theme.scale(2); }

    @Override
    protected WDropdownRoot createRootWidget() { return new Root(); }

    @Override
    protected WDropdownValue createValueWidget() { return new Value(); }

    @Override
    protected void onCalculateSize() {
        super.onCalculateSize();
        maxValueWidth = 0;
        for (int i = 0; i < values.length; i++) {
            captions[i] = values[i].toString();
            maxValueWidth = Math.max(maxValueWidth, theme().typography().width(captions[i], 11, false));
        }
        width = maxValueWidth + theme.scale(style().dropdownInset() + 18);
        height = theme.scale(style().inverted() ? 13 : 15);
        root.width = width;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        style().drawDropdownHeader(renderer, theme(), this, expanded, pressed || mouseOver);
        theme().typography().draw(renderer, caption(get()), x + theme.scale(style().dropdownInset()),
            y + (height - theme.scale(11)) / 2, 11, style().dropdownText(theme(), true), false);
    }

    private static class Root extends WDropdownRoot {
    }

    private class Value extends WDropdownValue implements HbWidget {
        @Override
        protected void onCalculateSize() {
            width = theme().typography().width(caption(value), 11, false) + theme.scale(12);
            height = theme.scale(18);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            boolean selected = value.equals(HbDropdown.this.value);
            style().drawDropdownValue(renderer, theme(), this, selected, pressed || mouseOver);
            theme().typography().draw(renderer, caption(value), x + theme.scale(6), y + theme.scale(3),
                11, style().dropdownText(theme(), selected), false);
        }
    }
}
