package com.hunterbuddy.hud.performance;

import com.hunterbuddy.hud.glass.GlassPainter;
import com.hunterbuddy.hud.toast.ModuleToastBlur;
import com.hunterbuddy.hud.text.HudText;
import com.hunterbuddy.hud.text.HudTextScale;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class PerformancePainter {
    private static final String TITLE = "Performance";
    private final GlassPainter glass = new GlassPainter();
    private final PerformanceLayout layout = new PerformanceLayout();
    private final String[] measured = new String[3];
    private final double[] labelWidths = new double[3];
    private final double[] valueWidths = new double[3];
    private final double[] reservedWidths = new double[3];
    private double scale;
    private double valueScale;
    private double labelHeight;
    private double valueHeight;
    private double titleHeight;
    private boolean compact;
    private boolean title;
    private boolean shadow;
    private boolean invalid = true;

    public void invalidate() { invalid = true; }

    public double width() { return layout.width; }

    public double height() { return layout.height; }

    public void measure(HudRenderer renderer, PerformanceMetric[] metrics, double scale, double textScale,
                        boolean compact, boolean title, boolean shadow) {
        double valueScale = Math.max(HudTextScale.NORMAL, HudTextScale.fixed(textScale));
        boolean changed = invalid || this.scale != scale || this.valueScale != valueScale || this.compact != compact
            || this.title != title || this.shadow != shadow;
        for (int i = 0; i < metrics.length; i++) changed |= measured[i] != metrics[i].text;
        if (!changed) return;
        this.scale = scale;
        this.valueScale = valueScale;
        this.compact = compact;
        this.title = title;
        this.shadow = shadow;
        invalid = false;
        labelHeight = HudText.textHeight(renderer, false, HudTextScale.SMALL);
        valueHeight = HudText.textHeight(renderer, shadow, valueScale);
        titleHeight = title ? HudText.textHeight(renderer, shadow, HudTextScale.NORMAL) : 0;
        double titleWidth = title ? HudText.textWidth(renderer, TITLE, shadow, HudTextScale.NORMAL) : 0;
        for (int i = 0; i < metrics.length; i++) {
            PerformanceMetric metric = metrics[i];
            measured[i] = metric.text;
            labelWidths[i] = HudText.textWidth(renderer, metric.label, false, HudTextScale.SMALL);
            valueWidths[i] = HudText.textWidth(renderer, metric.text, shadow, valueScale);
            reservedWidths[i] = Math.max(valueWidths[i], HudText.textWidth(renderer, metric.sample, shadow, valueScale));
        }
        layout.update(scale, compact, title, labelHeight, valueHeight, titleHeight, titleWidth, labelWidths, reservedWidths);
    }

    public void draw(HudRenderer renderer, PerformanceMetric[] metrics, double x, double y, long now,
                     boolean blur, boolean reflection, Color background, Color titleColor, Color critical) {
        if (blur) ModuleToastBlur.INSTANCE.draw(x, y, layout.width, layout.height, GlassPainter.radius(scale), 1);
        int level = 0;
        float flash = 0;
        for (int i = 0; i < metrics.length; i++) {
            level = Math.max(level, metrics[i].level);
            flash = Math.max(flash, metrics[i].flash);
        }
        double wave = 0.5 + 0.5 * Math.sin(now * 0.0026);
        double pulse = 0.65 + 0.35 * wave * wave;
        double alert = Math.max(level * 0.5, flash);
        if (alert > 0) {
            double halo = 2 * scale;
            glass.pill(renderer, x - halo, y - halo, layout.width + halo * 2, layout.height + halo * 2,
                (GlassPainter.radius(scale) + halo) / 5, glass.tint(critical, alert * pulse * 0.22));
        }
        glass.card(renderer, x, y, layout.width, layout.height, scale, 1, reflection, background);
        if (alert > 0) glass.outline(renderer, x, y, layout.width, layout.height, GlassPainter.radius(scale), 0.8 * scale,
            glass.tint(critical, 0.38 + alert * pulse * 0.36));
        double center = y + layout.rowY + layout.rowHeight / 2;
        if (title) {
            double titleCenter = compact ? center : y + layout.padding + titleHeight / 2;
            HudText.text(renderer, TITLE, x + layout.padding + (compact ? 3 * scale : 2 * scale),
                titleCenter - titleHeight / 2, titleColor, shadow, HudTextScale.NORMAL);
        }
        double left = x + layout.padding + layout.inlineTitle;
        for (int i = 0; i < metrics.length; i++) {
            metric(renderer, metrics[i], i, left, y + layout.rowY, center, pulse, reflection, background, titleColor);
            left += layout.widths[i] + layout.gap;
        }
    }

    private void metric(HudRenderer renderer, PerformanceMetric metric, int index, double x, double y, double center,
                        double pulse, boolean reflection, Color background, Color titleColor) {
        double alert = Math.max(metric.level * 0.5, metric.flash) * pulse;
        double width = layout.widths[index];
        double radius = 5 * scale;
        glass.pill(renderer, x, y, width, layout.rowHeight, scale, glass.tint(background, 0.46));
        glass.pill(renderer, x, y, width, layout.rowHeight, scale, glass.tint(metric.color, 0.055 + alert * 0.09));
        glass.outline(renderer, x, y, width, layout.rowHeight, radius, 0.8 * scale,
            glass.tint(metric.color, 0.32 + alert * 0.2));
        glass.accentBar(renderer, x, y, layout.rowHeight, scale * 0.65, 0.85, metric.color);
        if (reflection) renderer.quad(x + radius, y + scale, width - radius * 2, scale, glass.white(0.12));
        HudText.text(renderer, metric.label, x + layout.labelInset, center - labelHeight / 2,
            glass.tint(titleColor, 0.58), false, HudTextScale.SMALL);
        HudText.text(renderer, metric.text, x + width - layout.valueInset - valueWidths[index], center - valueHeight / 2,
            metric.color, shadow, valueScale);
    }
}
