package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public interface HbStyle {
    Color INPUT_BACKGROUND = HbPalette.rgb(0x111214);
    HbPalette palette();

    String fontFamily();

    default boolean inverted() { return false; }
    default boolean hasCounter() { return false; }
    default double moduleInset(boolean active) { return 8; }
    default double headerHeight() { return 23; }
    default double contentTop() { return 4; }
    default double contentBottom() { return 6; }
    default double settingInset() { return 14; }
    default double dropdownInset() { return 6; }
    default Color inputBackground() { return INPUT_BACKGROUND; }
    default Color inputText(HbGuiTheme theme) { return palette().text(); }

    default void drawWindowBody(GuiRenderer renderer, HbGuiTheme theme, double x, double y, double width, double height) {
        renderer.quad(x, y, width, height, palette().window());
    }

    default void drawWindowFrame(GuiRenderer renderer, HbGuiTheme theme, double x, double y, double width, double height) {
    }

    void drawWindowHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget header, boolean active, double phase);

    default Color moduleTextColor(HbGuiTheme theme, boolean active, boolean hover) {
        return active ? palette().text() : palette().dim();
    }

    void drawModuleRow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active, boolean hover, boolean risk, double pulse, double fill);

    void drawRisk(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active);

    default void drawModuleArrow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean expanded, boolean active) {
        HbDraw.arrow(renderer, row.x + row.width - theme.scale(10), row.y + row.height / 2,
            theme.scale(3), theme.scale(2), expanded, theme.accent());
    }

    default void drawModuleSettings(GuiRenderer renderer, HbGuiTheme theme, WWidget panel) {
        renderer.quad(panel.x - theme.scale(6), panel.y + theme.scale(2), panel.width + theme.scale(6),
            Math.max(0, panel.height - theme.scale(2)), palette().row());
    }

    default void drawFavorite(GuiRenderer renderer, HbGuiTheme theme, WWidget favorite, boolean checked) {
        HbDraw.diamond(renderer, favorite.x + favorite.width / 2, favorite.y + favorite.height / 2,
            theme.scale(4 * Math.sqrt(2) * 0.7), checked ? theme.accent() : palette().border());
    }

    void drawSlider(GuiRenderer renderer, HbGuiTheme theme, WWidget slider, double fraction);
    void drawCheckbox(GuiRenderer renderer, HbGuiTheme theme, WWidget box, boolean checked, boolean hover);

    default Color dropdownText(HbGuiTheme theme, boolean selected) { return palette().text(); }

    default void drawDropdownHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget dropdown, boolean expanded, boolean hover) {
        renderer.quad(dropdown, inputBackground());
        HbDraw.arrow(renderer, dropdown.x + dropdown.width - theme.scale(7), dropdown.y + dropdown.height / 2,
            theme.scale(2.5), theme.scale(1.5), expanded, palette().dim());
    }

    default void drawDropdownValue(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean selected, boolean hover) {
        renderer.quad(row, hover ? palette().hover() : inputBackground());
    }

    default void drawInput(GuiRenderer renderer, HbGuiTheme theme, WWidget input, boolean focused) {
        renderer.quad(input, inputBackground());
        HbDraw.border(renderer, input.x, input.y, input.width, input.height, theme.scale(1), palette().border());
    }

    default void drawButton(GuiRenderer renderer, HbGuiTheme theme, WWidget button, boolean filled, boolean hover) {
        renderer.quad(button, inputBackground());
        HbDraw.border(renderer, button.x, button.y, button.width, button.height, theme.scale(1), hover ? theme.accent() : palette().border());
    }

    default Color buttonText(HbGuiTheme theme, boolean filled) { return palette().text(); }
}
