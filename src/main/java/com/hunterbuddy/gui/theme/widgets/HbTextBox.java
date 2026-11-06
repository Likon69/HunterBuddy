package com.hunterbuddy.gui.theme.widgets;

import com.hunterbuddy.gui.theme.HbGuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.input.WMeteorTextBox;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class HbTextBox extends WMeteorTextBox implements HbWidget {
    private final boolean customRenderer;
    private boolean number;
    private boolean floating;
    private double cursorTime;

    public HbTextBox(String text, String placeholder, CharFilter filter, Class<? extends Renderer> renderer) {
        super(text, placeholder, filter, renderer);
        customRenderer = renderer != null;
    }

    @Override
    public HbGuiTheme theme() {
        return HbWidget.super.theme();
    }

    public void number(boolean floating) {
        number = true;
        this.floating = floating;
    }

    @Override
    public double pad() {
        return theme().hbScale(floating || number && !style().hasCounter() ? 0 : style().inverted() ? 2 : 4);
    }

    public double fontSize() {
        return customRenderer || number && !floating ? 11 : 10;
    }

    @Override
    protected void onCalculateSize() {
        super.onCalculateSize();
        width = theme().hbScale(number ? 30 : 110);
        height = theme().hbScale(floating ? 10 : number && style().hasCounter() ? 13 : 14);
        if (customRenderer) height = Math.max(height, theme().hbScale(theme().textSize(11) + 2));
    }

    @Override
    protected double getTextWidth(int position) {
        return theme().layoutUnits(super.getTextWidth(position)) * fontSize() / 11;
    }

    @Override
    protected void onCursorChanged() {
        cursorTime = 0;
    }

    @Override
    protected void onRender(GuiRenderer gui, double mouseX, double mouseY, double delta) {
        cursorTime += delta;
        if (!floating && (!number || style().hasCounter())) style().drawInput(gui, theme(), this, focused);
        double offset = getOverflowWidthForRender();
        double textY = y + (height - theme().hbScale(fontSize())) / 2;
        double textX = x + pad() - offset;
        if (number && !focused) {
            double textWidth = theme().typography().width(text, fontSize(), false);
            textX = floating ? x + (width - textWidth) / 2 : x + width - pad() - textWidth;
        }
        String visibleText = text.isEmpty() && placeholder != null ? placeholder : text;
        boolean clipped = focused || customRenderer || theme().typography().width(visibleText, fontSize(), false) > width - pad() * 2;
        if (clipped) gui.scissorStart(x + pad(), y, Math.max(0, width - pad() * 2), height);
        Color color = style().inputText(theme());
        if (!text.isEmpty()) {
            if (customRenderer) renderer.render(gui, textX, textY, text, color);
            else theme().typography().draw(gui, text, textX, textY, fontSize(), color, false);
        } else if (placeholder != null) {
            theme().typography().draw(gui, placeholder, textX, textY, 10, theme().placeholderColor.get(), false);
        }
        if (focused && selectionStart != selectionEnd) {
            double left = Math.min(getTextWidth(selectionStart), getTextWidth(selectionEnd));
            double right = Math.max(getTextWidth(selectionStart), getTextWidth(selectionEnd));
            gui.quad(textX + left, textY, right - left, theme().hbScale(fontSize()), theme().textHighlightColor.get());
        }
        if (focused && cursorTime % 1.2 < 0.6) {
            gui.quad(textX + getTextWidth(cursor), textY, theme().hbScale(1), theme().hbScale(fontSize()), theme().accent());
        }
        if (clipped) gui.scissorEnd();
    }
}
