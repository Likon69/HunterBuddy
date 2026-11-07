package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.Locale;

public final class PhosphoreStyle implements HbStyle {
    private static final HbPalette PALETTE = new HbPalette(
        HbPalette.rgb(0x040805, 0.94), HbPalette.rgb(0x09170A), HbPalette.rgb(0x0E260F),
        HbPalette.rgb(0x5CFF5C), HbPalette.rgb(0xCFE8CF), HbPalette.rgb(0x4C564C), HbPalette.rgb(0x1A2A1C),
        HbPalette.rgb(0x0A120B), HbPalette.rgb(0xC8A832), HbPalette.rgb(0x030503)
    );
    private static final int[] PRESETS = {0x5CFF5C, 0xFFB000, 0x33CCFF, 0xE0E0D0};
    private static final int SLIDER_CELLS = 10;
    private static final double ROW_TINT = 0.06;
    private static final double PANEL_TINT = 0.03;
    private static final double GLOW_ALPHA = 0.25;
    private final Color scanline = new Color(0, 0, 0, 56);
    private final Color tint = new Color();
    private final Color glow = new Color();

    @Override
    public HbPalette palette() { return PALETTE; }
    @Override
    public String fontFamily() { return "VT323"; }
    @Override
    public int[] accentPresets() { return PRESETS; }
    @Override
    public boolean strongTitle() { return false; }
    @Override
    public boolean lamp() { return false; }
    @Override
    public boolean counter() { return true; }
    @Override
    public double titleInset() { return 10; }
    @Override
    public double headerHeight() { return 24; }
    @Override
    public double moduleInset(boolean active) { return 30; }
    @Override
    public double settingInset() { return 22; }
    @Override
    public double dropdownInset() { return 10; }
    @Override
    public String windowTitle(String title) { return "> " + title.toUpperCase(Locale.ROOT); }
    @Override
    public Color titleColor(HbGuiTheme theme) { return theme.accent(); }

    @Override
    public void drawWindowFrame(GuiRenderer renderer, HbGuiTheme theme, double x, double y, double width, double height) {
        HbDraw.border(renderer, x, y, width, height, theme.hbScale(1), theme.colors().border());
        double bottom = y + height;
        for (double top = y + theme.hbScale(2); top < bottom; top += theme.hbScale(3)) {
            renderer.quad(x, top, width, Math.min(theme.hbScale(1), bottom - top), scanline);
        }
    }

    @Override
    public void drawWindowHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget header, boolean active, double phase) {
        renderer.quad(header, theme.colors().window());
        double inset = theme.hbScale(10);
        renderer.quad(header.x + inset, header.y + header.height - theme.hbScale(1), header.width - inset * 2, theme.hbScale(1), theme.colors().border());
    }

    @Override
    public void drawTitleTail(GuiRenderer renderer, HbGuiTheme theme, WWidget header, double x, boolean active) {
        if (!active || HbAnim.sweep(1) >= 0.5) return;
        double height = theme.hbScale(10);
        renderer.quad(x + theme.hbScale(4), header.y + (header.height - height) / 2, theme.hbScale(5), height, theme.accent());
    }

    @Override
    public Color moduleTextColor(HbGuiTheme theme, boolean active, boolean hover) {
        return active ? theme.accent() : hover ? theme.colors().text() : theme.colors().dim();
    }

    @Override
    public void drawModuleTitle(GuiRenderer renderer, HbGuiTheme theme, String title, double x, double y, Color color, boolean active) {
        if (active) {
            HbDraw.withAlpha(glow, color, GLOW_ALPHA);
            theme.typography().draw(renderer, title, x - theme.hbScale(1), y, 12, glow, false);
            theme.typography().draw(renderer, title, x + theme.hbScale(1), y, 12, glow, false);
        }
        theme.typography().draw(renderer, title, x, y, 12, color, false);
    }

    @Override
    public void drawModuleRow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active, boolean hover, boolean risk, double pulse, double fill) {
        if (fill > 0) renderer.quad(row, HbDraw.withAlpha(tint, theme.accent(), ROW_TINT * fill));
        if (risk) return;
        double height = theme.hbScale(10);
        double left = row.x + theme.hbScale(8);
        double top = row.y + (row.height - height) / 2;
        Color color = active || hover ? theme.accent() : theme.colors().dim();
        HbDraw.bracket(renderer, left, top, theme.hbScale(14), height, theme.hbScale(1), theme.hbScale(3), color);
        if (fill > 0) {
            double size = theme.hbScale(4);
            renderer.quad(left + (theme.hbScale(14) - size) / 2, top + (height - size) / 2, size, size, HbDraw.withAlpha(glow, theme.accent(), fill));
        }
    }

    @Override
    public void drawRisk(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active) {
        double height = theme.hbScale(10);
        double left = row.x + theme.hbScale(8);
        double top = row.y + (row.height - height) / 2;
        HbDraw.bracket(renderer, left, top, theme.hbScale(14), height, theme.hbScale(1), theme.hbScale(3), theme.colors().risk());
        theme.typography().draw(renderer, "!", left + (theme.hbScale(14) - theme.typography().width("!", 12, false)) / 2,
            row.y + theme.hbScale(4), 12, theme.colors().risk(), false);
    }

    @Override
    public void drawModuleArrow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean expanded, boolean active) {
        if (expanded) HbStyle.super.drawModuleArrow(renderer, theme, row, true, active);
    }

    @Override
    public void drawModuleSettings(GuiRenderer renderer, HbGuiTheme theme, WWidget panel) {
        double top = panel.y + theme.hbScale(2);
        double height = Math.max(0, panel.height - theme.hbScale(2));
        renderer.quad(panel.x, top, panel.width, height, HbDraw.withAlpha(tint, theme.accent(), PANEL_TINT));
        renderer.quad(panel.x, top, theme.hbScale(1), height, theme.colors().border());
    }

    @Override
    public void drawSlider(GuiRenderer renderer, HbGuiTheme theme, WWidget slider, double fraction) {
        double gap = theme.hbScale(2);
        double cell = Math.max(0, (slider.width - gap * (SLIDER_CELLS - 1)) / SLIDER_CELLS);
        double height = theme.hbScale(9);
        double top = slider.y + (slider.height - height) / 2;
        long filled = Math.round(fraction * SLIDER_CELLS);
        for (int i = 0; i < SLIDER_CELLS; i++) {
            renderer.quad(slider.x + i * (cell + gap), top, cell, height, i < filled ? theme.sliderLeft.get() : theme.sliderRight.get());
        }
    }

    @Override
    public void drawCheckbox(GuiRenderer renderer, HbGuiTheme theme, WWidget box, boolean checked, boolean pressed, boolean hover) {
        double width = theme.hbScale(14);
        double height = theme.hbScale(10);
        double left = box.x + (box.width - width) / 2;
        double top = box.y + (box.height - height) / 2;
        HbDraw.bracket(renderer, left, top, width, height, theme.hbScale(1), theme.hbScale(3),
            pressed || hover ? theme.accent() : theme.colors().edge(false, false));
        if (checked) {
            double size = theme.hbScale(4);
            renderer.quad(left + (width - size) / 2, top + (height - size) / 2, size, size, theme.checkboxColor.get());
        }
    }

    @Override
    public void drawDropdownHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget dropdown, boolean expanded, boolean pressed, boolean hover) {
        Color color = pressed || hover || expanded ? theme.accent() : theme.colors().dim();
        double cy = dropdown.y + dropdown.height / 2;
        double half = theme.hbScale(3);
        double left = dropdown.x + theme.hbScale(4);
        double right = dropdown.x + dropdown.width - theme.hbScale(4);
        renderer.triangle(left + theme.hbScale(2), cy - half, left - theme.hbScale(2), cy, left + theme.hbScale(2), cy + half, color);
        renderer.triangle(right - theme.hbScale(2), cy - half, right + theme.hbScale(2), cy, right - theme.hbScale(2), cy + half, color);
    }

    @Override
    public void drawDropdownBox(GuiRenderer renderer, HbGuiTheme theme, WWidget dropdown, boolean expanded, boolean pressed, boolean hover) {
        HbDraw.bracket(renderer, dropdown.x, dropdown.y, dropdown.width, dropdown.height, theme.hbScale(1), theme.hbScale(3),
            pressed || hover || expanded ? theme.accent() : theme.colors().edge(false, false));
        HbDraw.arrow(renderer, dropdown.x + dropdown.width - theme.hbScale(7), dropdown.y + dropdown.height / 2,
            theme.hbScale(2.5), theme.hbScale(1.5), expanded, pressed || hover ? theme.accent() : theme.colors().dim());
    }

    @Override
    public void drawTab(GuiRenderer renderer, HbGuiTheme theme, WWidget tab, boolean active, boolean pressed, boolean hover) {
        if (active || hover) {
            HbDraw.bracket(renderer, tab.x, tab.y, tab.width, tab.height, theme.hbScale(1), theme.hbScale(3), theme.accent());
        }
    }

    @Override
    public Color tabText(HbGuiTheme theme, boolean active, boolean hover) {
        return active ? theme.accent() : hover ? theme.colors().text() : theme.colors().dim();
    }

    @Override
    public void drawInput(GuiRenderer renderer, HbGuiTheme theme, WWidget input, boolean focused) {
        renderer.quad(input, theme.colors().input());
        renderer.quad(input.x, input.y + input.height - theme.hbScale(1), input.width, theme.hbScale(1),
            focused ? theme.accent() : theme.colors().border());
    }

    @Override
    public boolean buttonFilled(boolean emphasized, boolean pressed, boolean hover) { return emphasized || pressed; }

    @Override
    public void drawButton(GuiRenderer renderer, HbGuiTheme theme, WWidget button, boolean filled, boolean pressed, boolean hover) {
        if (filled) {
            renderer.quad(button, theme.accent());
            return;
        }
        HbDraw.bracket(renderer, button.x, button.y, button.width, button.height, theme.hbScale(1), theme.hbScale(3),
            hover ? theme.accent() : theme.colors().edge(false, false));
    }

    @Override
    public Color buttonText(HbGuiTheme theme, boolean filled, boolean hover) {
        return filled ? theme.colors().onAccent() : hover ? theme.accent() : theme.colors().text();
    }
}
