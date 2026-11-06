package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public interface HbStyle {
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
    default Color inputText(HbGuiTheme theme) { return theme.colors().text(); }

    default void drawWindowBody(GuiRenderer renderer, HbGuiTheme theme, double x, double y, double width, double height) {
        renderer.quad(x, y, width, height, theme.colors().window());
    }

    default void drawWindowFrame(GuiRenderer renderer, HbGuiTheme theme, double x, double y, double width, double height) {
    }

    void drawWindowHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget header, boolean active, double phase);

    default Color moduleTextColor(HbGuiTheme theme, boolean active, boolean hover) {
        return active ? theme.colors().text() : theme.colors().dim();
    }

    void drawModuleRow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active, boolean hover, boolean risk, double pulse, double fill);

    void drawRisk(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active);

    default void drawModuleArrow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean expanded, boolean active) {
        HbDraw.arrow(renderer, row.x + row.width - theme.hbScale(10), row.y + row.height / 2,
            theme.hbScale(3), theme.hbScale(2), expanded, theme.accent());
    }

    default void drawModuleSettings(GuiRenderer renderer, HbGuiTheme theme, WWidget panel) {
        renderer.quad(panel.x - theme.hbScale(6), panel.y + theme.hbScale(2), panel.width + theme.hbScale(6),
            Math.max(0, panel.height - theme.hbScale(2)), theme.colors().row());
    }

    default Color favoriteColor(HbGuiTheme theme, boolean checked, boolean pressed, boolean hover) {
        if (checked) return theme.favoriteColor.get();
        return pressed ? theme.accent() : hover ? theme.colors().dim() : theme.colors().border();
    }

    default double favoriteScale(boolean pressed, boolean hover) {
        return pressed ? 0.8 : hover ? 1.3 : 1;
    }

    default void drawFavorite(GuiRenderer renderer, HbGuiTheme theme, WWidget favorite, boolean checked, boolean pressed, boolean hover) {
        HbDraw.diamond(renderer, favorite.x + favorite.width / 2, favorite.y + favorite.height / 2,
            theme.hbScale(4 * Math.sqrt(2) * 0.7 * favoriteScale(pressed, hover)), favoriteColor(theme, checked, pressed, hover));
    }

    void drawSlider(GuiRenderer renderer, HbGuiTheme theme, WWidget slider, double fraction);
    void drawCheckbox(GuiRenderer renderer, HbGuiTheme theme, WWidget box, boolean checked, boolean pressed, boolean hover);

    default Color dropdownText(HbGuiTheme theme, boolean selected) { return theme.colors().text(); }

    default void drawDropdownHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget dropdown, boolean expanded, boolean pressed, boolean hover) {
        renderer.quad(dropdown, theme.colors().control(pressed, hover));
        if (pressed || hover || expanded) {
            HbDraw.border(renderer, dropdown.x, dropdown.y, dropdown.width, dropdown.height, theme.hbScale(1),
                theme.colors().edge(pressed || expanded, hover));
        }
        HbDraw.arrow(renderer, dropdown.x + dropdown.width - theme.hbScale(7), dropdown.y + dropdown.height / 2,
            theme.hbScale(2.5), theme.hbScale(1.5), expanded, hover || pressed ? theme.accent() : theme.colors().dim());
    }

    default void drawDropdownValue(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean selected, boolean pressed, boolean hover) {
        renderer.quad(row, theme.colors().control(pressed, hover));
        if (pressed || hover) renderer.quad(row.x, row.y, theme.hbScale(2), row.height, theme.accent());
    }

    default void drawInput(GuiRenderer renderer, HbGuiTheme theme, WWidget input, boolean focused) {
        renderer.quad(input, theme.colors().input());
        HbDraw.border(renderer, input.x, input.y, input.width, input.height, theme.hbScale(1), theme.colors().border());
    }

    default boolean buttonFilled(boolean emphasized, boolean pressed, boolean hover) { return false; }

    default void drawButton(GuiRenderer renderer, HbGuiTheme theme, WWidget button, boolean filled, boolean pressed, boolean hover) {
        renderer.quad(button, theme.colors().control(pressed, hover));
        HbDraw.border(renderer, button.x, button.y, button.width, button.height, theme.hbScale(pressed ? 2 : 1),
            pressed ? theme.accent() : theme.colors().edge(false, hover));
    }

    default Color buttonText(HbGuiTheme theme, boolean filled, boolean hover) {
        return hover ? theme.accent() : theme.colors().text();
    }
}
