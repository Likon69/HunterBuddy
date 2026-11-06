package com.hunterbuddy.gui.theme;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class LanterneStyle implements HbStyle {
    private static final HbPalette PALETTE = new HbPalette(
        HbPalette.rgb(0x111214, 0.92), HbPalette.rgb(0x17191C), HbPalette.rgb(0x1E2126),
        HbPalette.rgb(0xF2A33A), HbPalette.rgb(0xECE7DC), HbPalette.rgb(0x8A8780), HbPalette.rgb(0x2A2C30),
        HbPalette.rgb(0x111214), HbPalette.rgb(0xE05A4F), HbPalette.rgb(0x111214)
    );
    private static final double[] HALO_WIDTHS = {36, 24, 12};
    private static final double[] HALO_ALPHAS = {0.08, 0.14, 0.22};
    private final Color[] halos = {new Color(), new Color(), new Color()};

    @Override
    public HbPalette palette() { return PALETTE; }

    @Override
    public String fontFamily() { return "JetBrains Mono"; }

    @Override
    public double moduleInset(boolean active) { return active ? 11 : 8; }

    @Override
    public void drawWindowHeader(GuiRenderer renderer, HbGuiTheme theme, WWidget header, boolean active, double phase) {
        HbDraw.chamferedTop(renderer, header.x, header.y, header.width, header.height, theme.hbScale(6), theme.colors().row());
        renderer.quad(header.x, header.y + header.height - theme.hbScale(1), header.width, theme.hbScale(1), theme.colors().border());
    }

    @Override
    public void drawModuleRow(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active, boolean hover, boolean risk, double pulse, double fill) {
        if (hover) renderer.quad(row, theme.colors().hover());
        else if (active) renderer.quad(row, theme.colors().row());
        if (!active) return;
        double start = row.x + theme.hbScale(3);
        double breath = HbAnim.breath(3.5);
        for (int i = 0; i < HALO_WIDTHS.length; i++) {
            renderer.quad(start, row.y, Math.min(theme.hbScale(HALO_WIDTHS[i]), row.width - theme.hbScale(3)), row.height,
                HbDraw.withAlpha(halos[i], theme.accent(), HALO_ALPHAS[i] * breath));
        }
        renderer.quad(row.x, row.y, theme.hbScale(3), row.height, theme.accent());
    }

    @Override
    public void drawRisk(GuiRenderer renderer, HbGuiTheme theme, WWidget row, boolean active) {
        renderer.quad(row.x + row.width - theme.hbScale(3), row.y, theme.hbScale(3), row.height, theme.colors().risk());
    }

    @Override
    public void drawSlider(GuiRenderer renderer, HbGuiTheme theme, WWidget slider, double fraction) {
        double cy = slider.y + slider.height / 2;
        double cx = slider.x + slider.width * fraction;
        renderer.quad(slider.x, cy - theme.hbScale(1), slider.width, theme.hbScale(2), theme.sliderRight.get());
        renderer.quad(slider.x, cy - theme.hbScale(1), slider.width * fraction, theme.hbScale(2), theme.sliderLeft.get());
        renderer.quad(cx - theme.hbScale(3), cy - theme.hbScale(6), theme.hbScale(6), theme.hbScale(12), theme.sliderHandle.get(false, slider.mouseOver));
        renderer.quad(cx - theme.hbScale(0.5), cy - theme.hbScale(6), theme.hbScale(1), theme.hbScale(12), theme.sliderLeft.get());
    }

    @Override
    public void drawCheckbox(GuiRenderer renderer, HbGuiTheme theme, WWidget box, boolean checked, boolean pressed, boolean hover) {
        double size = theme.hbScale(10);
        double x = box.x + (box.width - size) / 2;
        double y = box.y + (box.height - size) / 2;
        renderer.quad(x, y, size, size, checked ? theme.checkboxColor.get() : theme.colors().control(pressed, hover));
        Color edge = pressed ? theme.accent() : checked && hover ? theme.colors().text() : checked ? theme.checkboxColor.get() : theme.colors().edge(false, hover);
        HbDraw.border(renderer, x, y, size, size, theme.hbScale(pressed || hover ? 2 : 1), edge);
        if (checked) renderer.quad(x + theme.hbScale(3), y + theme.hbScale(3), theme.hbScale(4), theme.hbScale(4), theme.colors().input());
    }
}
