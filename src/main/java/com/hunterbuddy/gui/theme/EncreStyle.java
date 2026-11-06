package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class EncreStyle implements HbStyle {
    private static final HbPalette PALETTE = new HbPalette(
        HbPalette.rgb(0x0B0B0E, 0.94), HbPalette.rgb(0x141418), HbPalette.rgb(0x141418),
        HbPalette.rgb(0xE8E4DA), HbPalette.rgb(0xE8E4DA), HbPalette.rgb(0x6F6C66), HbPalette.rgb(0x26262C)
    );
    private static final Color DARK = HbPalette.rgb(0x0B0B0E);
    private static final Color RISK = HbPalette.rgb(0xD7472F);
    private static final Color INACTIVE_RISK = HbPalette.rgb(0xD7472F, 0.5);

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
    public void drawWindowFrame(GuiRenderer renderer, HbGuiTheme theme, double x, double y, double width, double height) {
        HbDraw.border(renderer, x, y, width, height, theme.scale(1), PALETTE.border());
    }

    @Override
    public void drawWindowHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget header, boolean active, double phase) {
        renderer.quad(header, PALETTE.row());
        renderer.quad(header.x, header.y + header.height - theme.scale(2), header.width, theme.scale(2), theme.accent());
    }

    @Override
    public Color moduleTextColor(HbGuiTheme theme, boolean active, boolean hover) {
        return active ? DARK : PALETTE.dim();
    }

    @Override
    public void drawModuleRow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active, boolean hover, boolean risk, double pulse, double fill) {
        if (fill > 0) renderer.quad(row.x, row.y, row.width * fill, row.height, theme.accent());
    }

    @Override
    public void drawRisk(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active) {
        renderer.quad(row.x + row.width - theme.scale(3), row.y, theme.scale(3), row.height, active ? RISK : INACTIVE_RISK);
    }

    @Override
    public void drawModuleArrow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean expanded, boolean active) {
        HbDraw.arrow(renderer, row.x + row.width - theme.scale(10), row.y + row.height / 2, theme.scale(3), theme.scale(2), expanded,
            active ? DARK : theme.accent());
    }

    @Override
    public void drawFavorite(GuiRenderer renderer, HbGuiTheme theme, WWidget favorite, boolean checked) {
        double size = theme.scale(6);
        if (checked) renderer.quad(favorite.x + (favorite.width - size) / 2, favorite.y + (favorite.height - size) / 2, size, size, theme.accent());
    }

    @Override
    public void drawSlider(GuiRenderer renderer, HbGuiTheme theme, WWidget slider, double fraction) {
        double cy = slider.y + theme.scale(11);
        renderer.quad(slider.x, cy, slider.width, theme.scale(1), PALETTE.border());
        renderer.quad(slider.x + slider.width * fraction - theme.scale(1), cy - theme.scale(5), theme.scale(2), theme.scale(12), theme.accent());
    }

    @Override
    public void drawCheckbox(GuiRenderer renderer, HbGuiTheme theme, WWidget box, boolean checked, boolean hover) {
        double size = theme.scale(10);
        double x = box.x + (box.width - size) / 2;
        double y = box.y + (box.height - size) / 2;
        if (checked) renderer.quad(x, y, size, size, theme.accent());
        HbDraw.border(renderer, x, y, size, size, theme.scale(1), theme.accent());
    }

    @Override
    public Color dropdownText(HbGuiTheme theme, boolean selected) { return DARK; }

    @Override
    public void drawDropdownHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget dropdown, boolean expanded, boolean hover) {
        renderer.quad(dropdown, theme.accent());
        HbDraw.arrow(renderer, dropdown.x + dropdown.width - theme.scale(7), dropdown.y + dropdown.height / 2,
            theme.scale(2.5), theme.scale(1.5), expanded, DARK);
    }

    @Override
    public void drawDropdownValue(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean selected, boolean hover) {
        renderer.quad(row, theme.accent());
        if (selected || hover) renderer.quad(row.x + theme.scale(6), row.y + row.height - theme.scale(2),
            Math.max(0, row.width - theme.scale(12)), theme.scale(1), DARK);
    }

    @Override
    public void drawInput(GuiRenderer renderer, HbGuiTheme theme, WWidget input, boolean focused) {
        renderer.quad(input.x, input.y + input.height - theme.scale(1), input.width, theme.scale(1), theme.accent());
    }

    @Override
    public void drawButton(GuiRenderer renderer, HbGuiTheme theme, WWidget button, boolean filled, boolean hover) {
        if (filled) renderer.quad(button, theme.accent());
        else HbDraw.border(renderer, button.x, button.y, button.width, button.height, theme.scale(1), theme.accent());
    }

    @Override
    public Color buttonText(HbGuiTheme theme, boolean filled) { return filled ? DARK : theme.accent(); }
}
