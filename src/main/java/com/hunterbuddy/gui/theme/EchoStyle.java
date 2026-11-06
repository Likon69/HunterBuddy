package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class EchoStyle implements HbStyle {
    private static final HbPalette PALETTE = new HbPalette(
        HbPalette.rgb(0x070C10, 0.92), HbPalette.rgb(0x0C1419), HbPalette.rgb(0x0C1419),
        HbPalette.rgb(0x3EDFC6), HbPalette.rgb(0xD6E4E6), HbPalette.rgb(0x5F7378), HbPalette.rgb(0x1B2A30)
    );
    private static final double[] SWEEP_ALPHAS = {0.1, 0.2, 0.3, 0.45};
    private static final Color INPUT = HbPalette.rgb(0x060A0D);
    private static final Color RISK = HbPalette.rgb(0xFF6B57);
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
    public Color inputBackground() { return INPUT; }

    @Override
    public void drawWindowFrame(GuiRenderer renderer, HbGuiTheme theme, double x, double y, double width, double height) {
        double gap = theme.scale(8);
        double line = theme.scale(1);
        renderer.quad(x + gap, y, Math.max(0, width - gap * 2), line, PALETTE.border());
        renderer.quad(x + gap, y + height - line, Math.max(0, width - gap * 2), line, PALETTE.border());
        renderer.quad(x, y + gap, line, Math.max(0, height - gap * 2), PALETTE.border());
        renderer.quad(x + width - line, y + gap, line, Math.max(0, height - gap * 2), PALETTE.border());
    }

    @Override
    public void drawWindowHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget header, boolean active, double phase) {
        renderer.quad(header, PALETTE.window());
        double inset = theme.scale(6);
        double left = header.x + inset;
        double trackWidth = header.width - inset * 2;
        double trackY = header.y + theme.scale(22);
        renderer.quad(left, trackY, trackWidth, theme.scale(1), PALETTE.border());
        if (!active) return;
        renderer.scissorStart(left, trackY - theme.scale(1), trackWidth, theme.scale(3));
        double progress = (HbAnim.sweep(6) + phase / 6) % 1;
        double start = left + trackWidth * progress;
        for (int i = 0; i < SWEEP_ALPHAS.length; i++) {
            renderer.quad(start + theme.scale(i * 6), trackY - theme.scale(1), theme.scale(6), theme.scale(3),
                HbDraw.withAlpha(sweep[i], theme.accent(), SWEEP_ALPHAS[i]));
        }
        renderer.scissorEnd();
    }

    @Override
    public Color moduleTextColor(HbGuiTheme theme, boolean active, boolean hover) {
        return active || hover ? PALETTE.text() : PALETTE.dim();
    }

    @Override
    public void drawModuleRow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active, boolean hover, boolean risk, double pulse, double fill) {
        if (active) renderer.quad(row, HbDraw.withAlpha(tint, theme.accent(), 0.07));
        if (hover) renderer.quad(row.x, row.y, theme.scale(1), row.height, theme.accent());
        double cx = row.x + theme.scale(12);
        double cy = row.y + row.height / 2;
        if (active && !risk) HbDraw.diamond(renderer, cx, cy, theme.scale(2.5 * Math.sqrt(2)), theme.accent());
        else if (!risk) renderer.quad(row.x + theme.scale(10), cy - theme.scale(0.5), theme.scale(4), theme.scale(1), hover ? PALETTE.dim() : PALETTE.border());
        if (active && !risk && pulse < 1) {
            double eased = HbAnim.easeOut(pulse);
            double size = theme.scale(10 * (0.6 + 1.3 * eased));
            HbDraw.border(renderer, cx - size / 2, cy - size / 2, size, size, theme.scale(0.6 + 1.3 * eased),
                HbDraw.withAlpha(ping, theme.accent(), 0.9 * (1 - eased)));
        }
    }

    @Override
    public void drawRisk(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active) {
        renderer.quad(row.x + theme.scale(10), row.y + row.height / 2 - theme.scale(2), theme.scale(4), theme.scale(4), RISK);
    }

    @Override
    public void drawModuleSettings(GuiRenderer renderer, HbGuiTheme theme, WWidget panel) {
        renderer.quad(panel.x, panel.y + theme.scale(2), Math.max(0, panel.width - theme.scale(6)),
            Math.max(0, panel.height - theme.scale(2)), PALETTE.row());
    }

    @Override
    public void drawFavorite(GuiRenderer renderer, HbGuiTheme theme, WWidget favorite, boolean checked) {
        double right = favorite.x + favorite.width;
        renderer.triangle(right - theme.scale(8), favorite.y, right, favorite.y + theme.scale(8), right, favorite.y,
            checked ? theme.accent() : PALETTE.border());
    }

    @Override
    public void drawSlider(GuiRenderer renderer, HbGuiTheme theme, WWidget slider, double fraction) {
        double cy = slider.y + slider.height / 2;
        renderer.quad(slider.x, cy - theme.scale(1), slider.width, theme.scale(2), PALETTE.border());
        for (int i = 1; i < 10; i++) {
            renderer.quad(slider.x + slider.width * i / 10, cy - theme.scale(1), theme.scale(1), theme.scale(3), PALETTE.border());
        }
        renderer.quad(slider.x, cy - theme.scale(1), slider.width * fraction, theme.scale(2), theme.accent());
        HbDraw.diamond(renderer, slider.x + slider.width * fraction, cy, theme.scale(3.5 * Math.sqrt(2)), theme.accent());
    }

    @Override
    public void drawCheckbox(GuiRenderer renderer, HbGuiTheme theme, WWidget box, boolean checked, boolean hover) {
        double cx = box.x + box.width / 2;
        double cy = box.y + box.height / 2;
        HbDraw.border(renderer, cx - theme.scale(5), cy - theme.scale(5), theme.scale(10), theme.scale(10), theme.scale(1), PALETTE.border());
        if (checked) HbDraw.diamond(renderer, cx, cy, theme.scale(2 * Math.sqrt(2)), theme.accent());
    }

    @Override
    public double dropdownInset() { return 18; }

    @Override
    public void drawDropdownHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget dropdown, boolean expanded, boolean hover) {
        HbStyle.super.drawDropdownHeader(renderer, theme, dropdown, expanded, hover);
        HbDraw.diamond(renderer, dropdown.x + theme.scale(8), dropdown.y + dropdown.height / 2, theme.scale(2 * Math.sqrt(2)), theme.accent());
    }

    @Override
    public void drawInput(GuiRenderer renderer, HbGuiTheme theme, WWidget input, boolean focused) {
        renderer.quad(input, inputBackground());
    }

    @Override
    public void drawButton(GuiRenderer renderer, HbGuiTheme theme, WWidget button, boolean filled, boolean hover) {
        renderer.quad(button, inputBackground());
    }
}
