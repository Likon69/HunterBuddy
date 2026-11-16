package com.hunterbuddy.hud.glass;

import com.hunterbuddy.hud.toast.ModuleToastBlur;
import com.hunterbuddy.util.HudGlowPanel;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class GlassPanel {
    private static final Color WARN = new Color(255, 190, 70, 255);
    private static final Color CRITICAL = new Color(255, 70, 70, 255);
    private final GlassPainter painter = new GlassPainter();
    private final Setting<GlassStyle> style;
    private final Setting<Double> scale;
    private final Setting<Boolean> blur;
    private final Setting<SettingColor> background;
    private final Setting<SettingColor> accent;
    private final HudGlowPanel glow;

    public GlassPanel(SettingGroup general, SettingGroup panel, SettingGroup glowGroup) {
        style = general.add(new EnumSetting.Builder<GlassStyle>()
            .name("style").description("Glass: rounded glass card. Flat: square flat panel. Glow: translucent panel with a neon halo.")
            .defaultValue(GlassStyle.Glass).build());
        scale = panel.add(new DoubleSetting.Builder()
            .name("scale").description("Scale of the panel and spacing, independent of text size.")
            .defaultValue(1).min(0.5).sliderRange(0.5, 3).visible(this::glass).build());
        blur = panel.add(new BoolSetting.Builder()
            .name("background-blur").description("Blur the actual scene behind the card.")
            .defaultValue(true).visible(this::glass).build());
        background = panel.add(new ColorSetting.Builder()
            .name("background").description("Card background color and opacity.")
            .defaultValue(new SettingColor(17, 24, 35, 222)).visible(this::glass).build());
        accent = panel.add(new ColorSetting.Builder()
            .name("accent-color").description("Card accent color while everything is fine.")
            .defaultValue(new SettingColor(126, 200, 255, 235)).visible(this::glass).build());
        glow = new HudGlowPanel(glowGroup, () -> style.get() == GlassStyle.Glow);
    }

    private boolean glass() {
        return style.get() != GlassStyle.Glow;
    }

    public double scale() {
        return glass() ? scale.get() : 1;
    }

    public double padding() {
        if (!glass()) return glow.padding();
        return Math.max(8, 14 * scale());
    }

    public void draw(HudRenderer renderer, double x, double y, double width, double height,
                     HudGlowPanel.Severity severity) {
        if (!glass()) {
            glow.draw(renderer, x, y, width, height, severity);
            return;
        }
        double s = scale();
        double pad = padding();
        double left = x - pad;
        double top = y - pad;
        double w = width + pad * 2;
        double h = height + pad * 2;
        boolean flat = style.get() == GlassStyle.Flat;
        double radius = flat ? 0 : GlassPainter.radius(s);
        if (blur.get()) {
            ModuleToastBlur.INSTANCE.begin(2);
            ModuleToastBlur.INSTANCE.draw(left, top, w, h, radius, 1);
        }
        Color tint = switch (severity) {
            case OK -> accent.get();
            case WARN -> WARN;
            case CRITICAL -> CRITICAL;
        };
        if (flat) painter.flatCard(renderer, left, top, w, h, s, 1, background.get());
        else {
            painter.card(renderer, left, top, w, h, s, 1, true, background.get());
            painter.accentBar(renderer, left, top, h, s, 1, tint);
        }
        if (severity != HudGlowPanel.Severity.OK) {
            painter.outline(renderer, left, top, w, h, radius, 0.8 * s, painter.tint(tint, 0.55));
        }
    }
}
