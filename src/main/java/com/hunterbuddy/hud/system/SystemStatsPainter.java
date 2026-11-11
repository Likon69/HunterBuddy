package com.hunterbuddy.hud.system;

import com.hunterbuddy.hud.text.HudText;
import com.hunterbuddy.hud.text.HudTextScale;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class SystemStatsPainter {
    private static final String[] DIGITS = { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9" };
    private final StringBuilder template = new StringBuilder(64);
    private final Color track = new Color();
    private boolean invalid = true;
    private double scale;
    private int digits;
    private double width;
    private double height;
    private double labelHeight;
    private double valueHeight;
    private double rowHeight;
    private double gap;
    private double barHeight;
    private double graphHeight;

    public double width() { return width; }
    public double height() { return height; }

    public void invalidate() { invalid = true; }

    public void measure(HudRenderer renderer, int digits, double scale, boolean graph, int rows) {
        if (invalid || this.scale != scale || this.digits != digits) {
            invalid = false;
            this.scale = scale;
            this.digits = digits;
            labelHeight = HudText.textHeight(renderer, false, HudTextScale.NORMAL);
            valueHeight = HudText.textHeight(renderer, false, HudTextScale.SMALL);
            int widest = 0;
            double widestWidth = 0;
            for (int i = 0; i < DIGITS.length; i++) {
                double measured = HudText.textWidth(renderer, DIGITS[i], false, HudTextScale.SMALL);
                if (measured > widestWidth) {
                    widest = i;
                    widestWidth = measured;
                }
            }
            template.setLength(0);
            for (int part = 0; part < 2; part++) {
                if (part > 0) template.append(" / ");
                for (int digit = 0; digit < digits; digit++) template.append(DIGITS[widest]);
                template.append('.').append(DIGITS[widest]);
            }
            template.append(" GiB (");
            for (int digit = 0; digit < 3; digit++) template.append(DIGITS[widest]);
            template.append("%)");
            width = Math.max(180 * scale, HudText.textWidth(renderer, template.toString(), false, HudTextScale.SMALL)
                + Math.max(HudText.textWidth(renderer, "RAM", false, HudTextScale.NORMAL),
                    HudText.textWidth(renderer, "CPU", false, HudTextScale.NORMAL)) + 12 * scale);
            gap = 4 * scale;
            barHeight = Math.max(2, 4 * scale);
            graphHeight = Math.max(8, 18 * scale);
        }
        rowHeight = Math.max(labelHeight, valueHeight) + gap + barHeight + (graph ? gap + graphHeight : 0);
        height = rows * rowHeight + Math.max(0, rows - 1) * 10 * scale;
    }

    public void draw(HudRenderer renderer, SystemStatsSampler data, double x, double y, boolean ram, boolean cpu,
                     boolean graph, int length, double ramWarn, double ramCritical, double cpuWarn, double cpuCritical,
                     Color text, Color ramColor, Color cpuColor, Color warn, Color critical) {
        track.set(text.r, text.g, text.b, (int) Math.round(text.a * 0.12));
        if (ram) {
            metric(renderer, data, true, x, y, graph, length, text,
                level(data.ramUsage(), ramWarn, ramCritical, ramColor, warn, critical));
            y += rowHeight + 10 * scale;
        }
        if (cpu) metric(renderer, data, false, x, y, graph, length, text,
            level(data.cpuUsage(), cpuWarn, cpuCritical, cpuColor, warn, critical));
    }

    private static Color level(double value, double warnAt, double criticalAt, Color normal, Color warn, Color critical) {
        if (value >= Math.max(warnAt, criticalAt)) return critical;
        return value >= Math.min(warnAt, criticalAt) ? warn : normal;
    }

    private void metric(HudRenderer renderer, SystemStatsSampler data, boolean ram, double x, double y,
                        boolean graph, int length, Color text, Color tint) {
        double lineHeight = Math.max(labelHeight, valueHeight);
        HudText.text(renderer, ram ? "RAM" : "CPU", x, y + (lineHeight - labelHeight) / 2,
            text, false, HudTextScale.NORMAL);
        String value = ram ? data.ramText() : data.cpuText();
        double valueWidth = HudText.textWidth(renderer, value, false, HudTextScale.SMALL);
        HudText.text(renderer, value, x + width - valueWidth, y + (lineHeight - valueHeight) / 2,
            text, false, HudTextScale.SMALL);
        double barY = y + lineHeight + gap;
        renderer.quad(x, barY, width, barHeight, track);
        double usage = ram ? data.ramUsage() : data.cpuUsage();
        if (usage > 0) renderer.quad(x, barY, width * Math.clamp(usage / 100, 0, 1), barHeight, tint);
        if (graph) sparkline(renderer, data, ram, x, barY + barHeight + gap, width, graphHeight, length, tint);
    }

    private static void sparkline(HudRenderer renderer, SystemStatsSampler data, boolean ram, double x, double y,
                                  double width, double height, int length, Color tint) {
        int available = Math.min(data.count(), length);
        int points = Math.min(available, 31);
        if (points < 2) return;
        double previousX = 0;
        double previousY = 0;
        boolean previousValid = false;
        for (int point = 0; point < points; point++) {
            int index = point * (available - 1) / (points - 1);
            int nextIndex = point + 1 < points ? (point + 1) * (available - 1) / (points - 1) : available;
            double value = -1;
            for (int sample = index; sample < nextIndex; sample++) {
                value = Math.max(value, data.history(ram, available - 1 - sample));
            }
            double px = x + 1 + (width - 2) * (length - available + index) / (length - 1);
            double py = y + 1 + (height - 2) * (1 - Math.clamp(value / 100, 0, 1));
            if (value >= 0 && previousValid) stroke(renderer, previousX, previousY, px, py, tint);
            previousX = px;
            previousY = py;
            previousValid = value >= 0;
        }
    }

    private static void stroke(HudRenderer renderer, double x1, double y1, double x2, double y2, Color tint) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double distance = Math.hypot(dx, dy);
        if (distance <= 0) return;
        double nx = -dy * 0.5 / distance;
        double ny = dx * 0.5 / distance;
        renderer.triangle(x1 + nx, y1 + ny, x1 - nx, y1 - ny, x2 - nx, y2 - ny, tint);
        renderer.triangle(x1 + nx, y1 + ny, x2 - nx, y2 - ny, x2 + nx, y2 + ny, tint);
    }
}
