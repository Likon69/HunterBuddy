package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class EncreStyle implements HbStyle {
    private static final HbPalette PALETTE = new HbPalette(
        HbPalette.rgb(0x0B0B0E, 0.94), HbPalette.rgb(0x141418), HbPalette.rgb(0x141418),
        HbPalette.rgb(0xE8E4DA), HbPalette.rgb(0xE8E4DA), HbPalette.rgb(0x6F6C66), HbPalette.rgb(0x26262C),
        HbPalette.rgb(0x111214), HbPalette.rgb(0xD7472F), HbPalette.rgb(0x0B0B0E)
    );
    private static final double INACTIVE_RISK_ALPHA = 0.5;
    private static final double DOT_STEP = 6;
    private final Color wash = new Color();

    @Override
    public HbPalette palette() { return PALETTE; }
    @Override
    public String fontFamily() { return "IBM Plex Mono"; }
    @Override
    public boolean inverted() { return true; }
    @Override
    public double headerHeight() { return 24; }
    @Override
    public double moduleInset(boolean active) { return 10; }
    @Override
    public double settingInset() { return 18; }
    @Override
    public Color inputText(HbGuiTheme theme) { return theme.accent(); }

    @Override
    public void drawGridLine(HbQuadSink sink, double x, double y, double length, boolean vertical, double thickness, boolean major, Color color) {
        if (!major) return;
        double step = thickness * DOT_STEP;
        for (double offset = 0; offset < length; offset += step) {
            if (vertical) sink.quad(x, y + offset, thickness, thickness, color);
            else sink.quad(x + offset, y, thickness, thickness, color);
        }
    }

    @Override
    public void drawWindowFrame(GuiRenderer renderer, HbGuiTheme theme, double x, double y, double width, double height) {
        HbDraw.border(renderer, x, y, width, height, theme.hbScale(1), theme.colors().border());
    }

    @Override
    public void drawWindowHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget header, boolean active, double phase) {
        renderer.quad(header, theme.colors().row());
        renderer.quad(header.x, header.y + header.height - theme.hbScale(2), header.width, theme.hbScale(2), theme.accent());
    }

    @Override
    public Color moduleTextColor(HbGuiTheme theme, boolean active, boolean hover) {
        return active ? theme.colors().onAccent() : theme.colors().dim();
    }

    @Override
    public void drawModuleRow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active, boolean hover, boolean risk, double pulse, double fill) {
        if (active || hover) renderer.quad(row, theme.colors().row());
        if (fill > 0) renderer.quad(row.x, row.y, row.width * fill, row.height, theme.accent());
    }

    @Override
    public void drawRisk(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active) {
        renderer.quad(row.x + row.width - theme.hbScale(3), row.y, theme.hbScale(3), row.height, active ? theme.colors().risk() : HbDraw.withAlpha(wash, theme.colors().risk(), INACTIVE_RISK_ALPHA));
    }

    @Override
    public void drawModuleArrow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean expanded, boolean active) {
        HbDraw.arrow(renderer, row.x + row.width - theme.hbScale(10), row.y + row.height / 2, theme.hbScale(3), theme.hbScale(2), expanded,
            active ? theme.colors().onAccent() : theme.accent());
    }

    @Override
    public void drawFavorite(GuiRenderer renderer, HbGuiTheme theme, WWidget favorite, boolean checked, boolean pressed, boolean hover) {
        double size = theme.hbScale(6);
        double x = favorite.x + (favorite.width - size) / 2;
        double y = favorite.y + (favorite.height - size) / 2;
        Color color = checked ? theme.favoriteColor.get() : theme.accent();
        if (checked || pressed) renderer.quad(x, y, size, size, color);
        if (hover || pressed) HbDraw.border(renderer, x - theme.hbScale(1), y - theme.hbScale(1), size + theme.hbScale(2), size + theme.hbScale(2), theme.hbScale(1), color);
    }

    @Override
    public void drawSlider(GuiRenderer renderer, HbGuiTheme theme, WWidget slider, double fraction) {
        double cy = slider.y + theme.hbScale(11);
        renderer.quad(slider.x, cy, slider.width, theme.hbScale(1), theme.sliderRight.get());
        renderer.quad(slider.x + slider.width * fraction - theme.hbScale(1), cy - theme.hbScale(5), theme.hbScale(2), theme.hbScale(12), theme.sliderLeft.get());
    }

    @Override
    public void drawCheckbox(GuiRenderer renderer, HbGuiTheme theme, WWidget box, boolean checked, boolean pressed, boolean hover) {
        double size = theme.hbScale(10);
        double x = box.x + (box.width - size) / 2;
        double y = box.y + (box.height - size) / 2;
        if (checked) renderer.quad(x, y, size, size, theme.checkboxColor.get());
        else if (pressed || hover) renderer.quad(x, y, size, size, HbDraw.withAlpha(wash, theme.checkboxColor.get(), pressed ? 0.6 : 0.3));
        HbDraw.border(renderer, x, y, size, size, theme.hbScale(1), theme.checkboxColor.get());
        if (checked && (pressed || hover)) HbDraw.border(renderer, x + theme.hbScale(2), y + theme.hbScale(2), size - theme.hbScale(4), size - theme.hbScale(4), theme.hbScale(1), theme.colors().onAccent());
    }

    @Override
    public Color dropdownText(HbGuiTheme theme, boolean selected) { return theme.colors().onAccent(); }

    @Override
    public void drawDropdownHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget dropdown, boolean expanded, boolean pressed, boolean hover) {
        renderer.quad(dropdown, theme.accent());
        if (pressed) HbDraw.border(renderer, dropdown.x + theme.hbScale(1), dropdown.y + theme.hbScale(1), dropdown.width - theme.hbScale(2), dropdown.height - theme.hbScale(2), theme.hbScale(1), theme.colors().onAccent());
        else if (hover) renderer.quad(dropdown.x, dropdown.y + dropdown.height - theme.hbScale(2), dropdown.width, theme.hbScale(2), theme.colors().onAccent());
        HbDraw.arrow(renderer, dropdown.x + dropdown.width - theme.hbScale(7), dropdown.y + dropdown.height / 2,
            theme.hbScale(2.5), theme.hbScale(1.5), expanded, theme.colors().onAccent());
    }

    @Override
    public void drawDropdownValue(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean selected, boolean pressed, boolean hover) {
        renderer.quad(row, theme.accent());
        if (pressed) HbDraw.border(renderer, row.x + theme.hbScale(1), row.y + theme.hbScale(1), row.width - theme.hbScale(2), row.height - theme.hbScale(2), theme.hbScale(1), theme.colors().onAccent());
        if (selected || hover) renderer.quad(row.x + theme.hbScale(6), row.y + row.height - theme.hbScale(2),
            Math.max(0, row.width - theme.hbScale(12)), theme.hbScale(hover ? 2 : 1), theme.colors().onAccent());
    }

    @Override
    public void drawTab(GuiRenderer renderer, HbGuiTheme theme, WWidget tab, boolean active, boolean pressed, boolean hover) {
        if (active || hover) renderer.quad(tab, theme.accent());
        else HbDraw.border(renderer, tab.x, tab.y, tab.width, tab.height, theme.hbScale(1), theme.colors().border());
    }

    @Override
    public Color tabText(HbGuiTheme theme, boolean active, boolean hover) {
        return active || hover ? theme.colors().onAccent() : theme.accent();
    }

    @Override
    public void drawInput(GuiRenderer renderer, HbGuiTheme theme, WWidget input, boolean focused) {
        renderer.quad(input.x, input.y + input.height - theme.hbScale(1), input.width, theme.hbScale(1), theme.accent());
    }

    @Override
    public boolean buttonFilled(boolean emphasized, boolean pressed, boolean hover) { return emphasized || pressed || hover; }

    @Override
    public void drawButton(GuiRenderer renderer, HbGuiTheme theme, WWidget button, boolean filled, boolean pressed, boolean hover) {
        if (!filled) {
            HbDraw.border(renderer, button.x, button.y, button.width, button.height, theme.hbScale(1), theme.accent());
            return;
        }
        renderer.quad(button, theme.accent());
        if (pressed) HbDraw.border(renderer, button.x + theme.hbScale(1), button.y + theme.hbScale(1), button.width - theme.hbScale(2), button.height - theme.hbScale(2), theme.hbScale(1), theme.colors().onAccent());
        else if (hover) renderer.quad(button.x, button.y + button.height - theme.hbScale(2), button.width, theme.hbScale(2), theme.colors().onAccent());
    }

    @Override
    public Color buttonText(HbGuiTheme theme, boolean filled, boolean hover) { return filled ? theme.colors().onAccent() : theme.accent(); }
}
