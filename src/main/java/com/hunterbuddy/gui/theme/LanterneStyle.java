package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class LanterneStyle implements HbStyle {
    private static final HbPalette PALETTE = new HbPalette(
        HbPalette.rgb(0x111214, 0.92), HbPalette.rgb(0x17191C), HbPalette.rgb(0x1E2126),
        HbPalette.rgb(0xF2A33A), HbPalette.rgb(0xECE7DC), HbPalette.rgb(0x8A8780), HbPalette.rgb(0x2A2C30)
    );
    private static final double[] HALO_WIDTHS = {36, 24, 12};
    private static final double[] HALO_ALPHAS = {0.08, 0.14, 0.22};
    private static final Color RISK = HbPalette.rgb(0xE05A4F);
    private final Color[] halos = {new Color(), new Color(), new Color()};

    @Override
    public HbPalette palette() { return PALETTE; }

    @Override
    public String fontFamily() { return "JetBrains Mono"; }

    @Override
    public double moduleInset(boolean active) { return active ? 11 : 8; }

    @Override
    public void drawWindowHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget header, boolean active, double phase) {
        HbDraw.chamferedTop(renderer, header.x, header.y, header.width, header.height, theme.scale(6), PALETTE.row());
        renderer.quad(header.x, header.y + header.height - theme.scale(1), header.width, theme.scale(1), PALETTE.border());
    }

    @Override
    public void drawModuleRow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active, boolean hover, boolean risk, double pulse, double fill) {
        if (hover) renderer.quad(row, PALETTE.hover());
        if (!active) return;
        double start = row.x + theme.scale(3);
        double breath = HbAnim.breath(3.5);
        for (int i = 0; i < HALO_WIDTHS.length; i++) {
            renderer.quad(start, row.y, Math.min(theme.scale(HALO_WIDTHS[i]), row.width - theme.scale(3)), row.height,
                HbDraw.withAlpha(halos[i], theme.accent(), HALO_ALPHAS[i] * breath));
        }
        renderer.quad(row.x, row.y, theme.scale(3), row.height, theme.accent());
    }

    @Override
    public void drawRisk(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active) {
        renderer.quad(row.x + row.width - theme.scale(3), row.y, theme.scale(3), row.height, RISK);
    }

    @Override
    public void drawSlider(GuiRenderer renderer, HbGuiTheme theme, WWidget slider, double fraction) {
        double cy = slider.y + slider.height / 2;
        double cx = slider.x + slider.width * fraction;
        renderer.quad(slider.x, cy - theme.scale(1), slider.width, theme.scale(2), PALETTE.border());
        renderer.quad(slider.x, cy - theme.scale(1), slider.width * fraction, theme.scale(2), theme.accent());
        renderer.quad(cx - theme.scale(3), cy - theme.scale(6), theme.scale(6), theme.scale(12), PALETTE.text());
        renderer.quad(cx - theme.scale(0.5), cy - theme.scale(6), theme.scale(1), theme.scale(12), theme.accent());
    }

    @Override
    public void drawCheckbox(GuiRenderer renderer, HbGuiTheme theme, WWidget box, boolean checked, boolean hover) {
        double size = theme.scale(10);
        double x = box.x + (box.width - size) / 2;
        double y = box.y + (box.height - size) / 2;
        renderer.quad(x, y, size, size, checked ? theme.accent() : inputBackground());
        HbDraw.border(renderer, x, y, size, size, theme.scale(1), checked ? theme.accent() : PALETTE.border());
        if (checked) renderer.quad(x + theme.scale(3), y + theme.scale(3), theme.scale(4), theme.scale(4), inputBackground());
    }
}
