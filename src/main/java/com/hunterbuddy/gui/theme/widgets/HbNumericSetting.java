package com.hunterbuddy.gui.theme.widgets;

import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;

import java.util.Locale;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;

public class HbNumericSetting extends WContainer implements HbWidget {
    private final DoubleSupplier read;
    private final DoublePredicate write;
    private final double min;
    private final double max;
    private final int decimals;
    private final boolean release;
    private final DecimalFormat formatter;
    private double displayedValue = Double.NaN;
    private String displayed;
    private HbSlider slider;
    private HbTextBox field;
    private double preview;

    public HbNumericSetting(IntSetting setting) {
        this(setting::get, value -> setting.set((int) Math.round(value)), setting.sliderMin, setting.sliderMax, 0, false);
    }

    public HbNumericSetting(DoubleSetting setting) {
        this(setting::get, setting::set, setting.sliderMin, setting.sliderMax, setting.decimalPlaces, setting.onSliderRelease);
    }

    private HbNumericSetting(DoubleSupplier read, DoublePredicate write, double min, double max, int decimals, boolean release) {
        this.read = read;
        this.write = write;
        this.min = min;
        this.max = max;
        this.decimals = decimals;
        this.release = release;
        formatter = new DecimalFormat(decimals == 0 ? "0" : "0." + "0".repeat(decimals), DecimalFormatSymbols.getInstance(Locale.ROOT));
    }

    @Override
    public void init() {
        preview = read.getAsDouble();
        field = new HbTextBox(format(), null, (text, character) -> Character.isDigit(character) || character == '-' || character == '.' || character == '+', null);
        field.number(style().inverted());
        add(field);
        slider = add(new HbSlider(preview, min, max)).widget();
        slider.step = decimals == 0 ? 1 : 0.05;
        slider.action = () -> {
            preview = decimals == 0 ? Math.round(slider.get()) : slider.get();
            updateField();
            if (!release) commit();
        };
        slider.actionOnRelease = this::commit;
        field.actionOnUnfocused = () -> {
            try {
                preview = Double.parseDouble(field.get());
            } catch (NumberFormatException exception) {
                preview = read.getAsDouble();
            }
            commit();
        };
    }

    private String format() {
        if (displayed == null || Double.compare(displayedValue, preview) != 0) {
            displayed = formatter.format(preview);
            displayedValue = preview;
        }
        return displayed;
    }

    private void commit() {
        if (Double.compare(read.getAsDouble(), preview) != 0 && !write.test(preview)) preview = read.getAsDouble();
        slider.set(preview);
        updateField();
    }

    public void refresh() {
        preview = read.getAsDouble();
        slider.set(preview);
        updateField();
    }

    private void updateField() {
        String text = format();
        if (!text.equals(field.get())) field.set(text);
    }

    @Override
    protected void onCalculateSize() {
        width = style().inverted() ? theme().hbScale(120)
            : theme().hbScale(98) + Math.max(theme().hbScale(30), theme().typography().width(field.get(), field.fontSize(), false) + field.pad() * 2);
        height = theme().hbScale(22);
    }

    @Override
    protected void onCalculateWidgetPositions() {
        slider.x = x;
        slider.y = y + theme().hbScale(style().inverted() ? 2 : 5);
        field.width = Math.min(width * 0.4, Math.max(theme().hbScale(style().inverted() ? 24 : 30), theme().typography().width(field.get(), field.fontSize(), false) + field.pad() * 2));
        slider.width = style().inverted() ? width : Math.max(theme().hbScale(40), width - field.width - theme().hbScale(8));
        slider.height = theme().hbScale(style().inverted() ? 18 : 12);
        field.x = style().inverted() ? x + slider.fraction() * Math.max(0, width - field.width) : x + width - field.width;
        field.y = style().inverted() ? y - theme().hbScale(2) : y + theme().hbScale(4);
    }

    @Override
    protected void onRender(meteordevelopment.meteorclient.gui.renderer.GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (style().inverted()) field.x = x + slider.fraction() * Math.max(0, width - field.width);
    }
}
