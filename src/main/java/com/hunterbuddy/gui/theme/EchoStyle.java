package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class EchoStyle implements HbStyle {
    private static final HbPalette PALETTE = new HbPalette(
        HbPalette.rgb(0x070C10, 0.92), HbPalette.rgb(0x0C1419), HbPalette.rgb(0x0C1419),
        HbPalette.rgb(0x3EDFC6), HbPalette.rgb(0xD6E4E6), HbPalette.rgb(0x5F7378), HbPalette.rgb(0x1B2A30),
        HbPalette.rgb(0x060A0D), HbPalette.rgb(0xFF6B57), HbPalette.rgb(0x070C10)
    );
    private static final double[] SWEEP_ALPHAS = {0.1, 0.2, 0.3, 0.45};
    private final Color[] sweep = {new Color(), new Color(), new Color(), new Color()};
    private final Color tint = new Color();
    private final Color ping = new Color();

    @Override
    public HbPalette palette() { return PALETTE; }
    @Override
    public String fontFamily() { return "Share Tech Mono"; }
    @Override
    public boolean hasCounter() { return true; }
    @Override
    public double contentTop() { return 6; }
    @Override
    public double contentBottom() { return 8; }
    @Override
    public double moduleInset(boolean active) { return 22; }
    @Override
    public double settingInset() { return 22; }
    @Override
    public void drawWindowFrame(GuiRenderer renderer, HbGuiTheme theme, double x, double y, double width, double height) {
        double gap = theme.hbScale(8);
        double line = theme.hbScale(1);
        renderer.quad(x + gap, y, Math.max(0, width - gap * 2), line, theme.colors().border());
        renderer.quad(x + gap, y + height - line, Math.max(0, width - gap * 2), line, theme.colors().border());
        renderer.quad(x, y + gap, line, Math.max(0, height - gap * 2), theme.colors().border());
        renderer.quad(x + width - line, y + gap, line, Math.max(0, height - gap * 2), theme.colors().border());
    }

    @Override
    public void drawWindowHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget header, boolean active, double phase) {
        renderer.quad(header, theme.colors().window());
        double inset = theme.hbScale(6);
        double left = header.x + inset;
        double trackWidth = header.width - inset * 2;
        double trackY = header.y + theme.hbScale(22);
        renderer.quad(left, trackY, trackWidth, theme.hbScale(1), theme.colors().border());
        if (!active) return;
        renderer.scissorStart(left, trackY - theme.hbScale(1), trackWidth, theme.hbScale(3));
        double progress = (HbAnim.sweep(6) + phase / 6) % 1;
        double start = left + trackWidth * progress;
        for (int i = 0; i < SWEEP_ALPHAS.length; i++) {
            renderer.quad(start + theme.hbScale(i * 6), trackY - theme.hbScale(1), theme.hbScale(6), theme.hbScale(3),
                HbDraw.withAlpha(sweep[i], theme.accent(), SWEEP_ALPHAS[i]));
        }
        renderer.scissorEnd();
    }

    @Override
    public Color moduleTextColor(HbGuiTheme theme, boolean active, boolean hover) {
        return active || hover ? theme.colors().text() : theme.colors().dim();
    }

    @Override
    public void drawModuleRow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active, boolean hover, boolean risk, double pulse, double fill) {
        if (active) {
            renderer.quad(row, theme.colors().row());
            renderer.quad(row, HbDraw.withAlpha(tint, theme.accent(), 0.07));
        }
        if (hover) {
            renderer.quad(row, HbDraw.withAlpha(tint, theme.accent(), 0.12));
            renderer.quad(row.x, row.y, theme.hbScale(2), row.height, theme.accent());
        }
        double cx = row.x + theme.hbScale(12);
        double cy = row.y + row.height / 2;
        if (active && !risk) HbDraw.diamond(renderer, cx, cy, theme.hbScale(2.5 * Math.sqrt(2)), theme.accent());
        else if (!risk) renderer.quad(row.x + theme.hbScale(10), cy - theme.hbScale(0.5), theme.hbScale(4), theme.hbScale(1), hover ? theme.colors().dim() : theme.colors().border());
        if (active && !risk && pulse < 1) {
            double eased = HbAnim.easeOut(pulse);
            double size = theme.hbScale(10 * (0.6 + 1.3 * eased));
            HbDraw.border(renderer, cx - size / 2, cy - size / 2, size, size, theme.hbScale(0.6 + 1.3 * eased),
                HbDraw.withAlpha(ping, theme.accent(), 0.9 * (1 - eased)));
        }
    }

    @Override
    public void drawRisk(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active) {
        renderer.quad(row.x + theme.hbScale(10), row.y + row.height / 2 - theme.hbScale(2), theme.hbScale(4), theme.hbScale(4), theme.colors().risk());
    }

    @Override
    public void drawModuleSettings(GuiRenderer renderer, HbGuiTheme theme, WWidget panel) {
        renderer.quad(panel.x, panel.y + theme.hbScale(2), Math.max(0, panel.width - theme.hbScale(6)),
            Math.max(0, panel.height - theme.hbScale(2)), theme.colors().row());
    }

    @Override
    public void drawFavorite(GuiRenderer renderer, HbGuiTheme theme, WWidget favorite, boolean checked, boolean pressed, boolean hover) {
        double right = favorite.x + favorite.width;
        double size = theme.hbScale(8 * favoriteScale(pressed, hover));
        renderer.triangle(right - size, favorite.y, right, favorite.y + size, right, favorite.y,
            favoriteColor(theme, checked, pressed, hover));
    }

    @Override
    public void drawSlider(GuiRenderer renderer, HbGuiTheme theme, WWidget slider, double fraction) {
        double cy = slider.y + slider.height / 2;
        renderer.quad(slider.x, cy - theme.hbScale(1), slider.width, theme.hbScale(2), theme.sliderRight.get());
        for (int i = 1; i < 10; i++) {
            renderer.quad(slider.x + slider.width * i / 10, cy - theme.hbScale(1), theme.hbScale(1), theme.hbScale(3), theme.sliderRight.get());
        }
        renderer.quad(slider.x, cy - theme.hbScale(1), slider.width * fraction, theme.hbScale(2), theme.sliderLeft.get());
        HbDraw.diamond(renderer, slider.x + slider.width * fraction, cy, theme.hbScale(3.5 * Math.sqrt(2)), theme.sliderLeft.get());
    }

    @Override
    public void drawCheckbox(GuiRenderer renderer, HbGuiTheme theme, WWidget box, boolean checked, boolean pressed, boolean hover) {
        double cx = box.x + box.width / 2;
        double cy = box.y + box.height / 2;
        if (pressed || hover) renderer.quad(cx - theme.hbScale(5), cy - theme.hbScale(5), theme.hbScale(10), theme.hbScale(10), HbDraw.withAlpha(tint, theme.accent(), pressed ? 0.35 : 0.15));
        HbDraw.border(renderer, cx - theme.hbScale(5), cy - theme.hbScale(5), theme.hbScale(10), theme.hbScale(10), theme.hbScale(1),
            pressed ? theme.accent() : theme.colors().edge(false, hover));
        if (checked) HbDraw.diamond(renderer, cx, cy, theme.hbScale(2 * Math.sqrt(2)), theme.checkboxColor.get());
    }

    @Override
    public double dropdownInset() { return 18; }

    @Override
    public void drawDropdownHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget dropdown, boolean expanded, boolean pressed, boolean hover) {
        HbStyle.super.drawDropdownHeader(renderer, theme, dropdown, expanded, pressed, hover);
        if (pressed || hover) renderer.quad(dropdown.x, dropdown.y, theme.hbScale(2), dropdown.height, theme.accent());
        HbDraw.diamond(renderer, dropdown.x + theme.hbScale(8), dropdown.y + dropdown.height / 2, theme.hbScale(2 * Math.sqrt(2)), theme.accent());
    }

    @Override
    public void drawDropdownBox(GuiRenderer renderer, HbGuiTheme theme, WWidget dropdown, boolean expanded, boolean pressed, boolean hover) {
        HbStyle.super.drawDropdownHeader(renderer, theme, dropdown, expanded, pressed, hover);
        if (pressed || hover) renderer.quad(dropdown.x, dropdown.y, theme.hbScale(2), dropdown.height, theme.accent());
    }

    @Override
    public void drawInput(GuiRenderer renderer, HbGuiTheme theme, WWidget input, boolean focused) {
        renderer.quad(input, theme.colors().input());
    }

    @Override
    public void drawButton(GuiRenderer renderer, HbGuiTheme theme, WWidget button, boolean filled, boolean pressed, boolean hover) {
        renderer.quad(button, theme.colors().control(pressed, hover));
        if (pressed) renderer.quad(button, HbDraw.withAlpha(tint, theme.accent(), 0.3));
        if (pressed || hover) renderer.quad(button.x, button.y, theme.hbScale(2), button.height, theme.accent());
    }
}
