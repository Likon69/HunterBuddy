package com.hunterbuddy.hud.toast;

import com.hunterbuddy.hud.glass.GlassPainter;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public final class ModuleToastPainter {
    private static final double TITLE = 0.88;
    private static final double SMALL = 0.62;
    private static final double STATE = 0.65;
    private final GlassPainter glass = new GlassPainter();
    private final Label[] labels = new Label[6];

    private static final class Label {
        ModuleToastTimeline.Notice notice;
        double width;
        double scale;
        double textScale;
        boolean compact;
        double statePill;
        String title;
        String bind;
        double bindWidth;
        double stateWidth;
    }

    public ModuleToastPainter() {
        for (int i = 0; i < labels.length; i++) labels[i] = new Label();
    }

    public void invalidate() {
        for (Label label : labels) label.notice = null;
    }

    public static double height(HudRenderer renderer, double scale, double textScale, boolean compact) {
        double top = topHeight(renderer, scale, textScale);
        return compact ? top + 14 * scale : top + pillHeight(renderer, scale, textScale) + 26 * scale;
    }

    private static double topHeight(HudRenderer renderer, double scale, double textScale) {
        return Math.max(renderer.textHeight(false, TITLE * textScale), pillHeight(renderer, scale, textScale));
    }

    private static double pillHeight(HudRenderer renderer, double scale, double textScale) {
        return renderer.textHeight(false, STATE * textScale) + 6 * scale;
    }

    public void draw(HudRenderer renderer, int index, ModuleToastTimeline.Notice notice, double x, double y,
                     double width, double height, double scale, double textScale, boolean compact, double alpha,
                     double remaining, boolean reflection, boolean blur, Color accent, Color background, Color text) {
        if (alpha < 0.002) return;
        if (blur) ModuleToastBlur.INSTANCE.draw(x, y, width, height, GlassPainter.radius(scale), alpha);
        Label label = labels[index];
        if (label.notice != notice || label.width != width || label.scale != scale
            || label.textScale != textScale || label.compact != compact) {
            label.notice = notice;
            label.width = width;
            label.scale = scale;
            label.textScale = textScale;
            label.compact = compact;
            label.bind = GlassPainter.fit(renderer, notice.bind(), width * 0.48 - 18 * scale, SMALL * textScale);
            label.bindWidth = renderer.textWidth(label.bind, false, SMALL * textScale) + 16 * scale;
            label.stateWidth = renderer.textWidth(notice.enabled() ? "ON" : "OFF", false, STATE * textScale);
            label.statePill = Math.max(43 * scale, label.stateWidth + 14 * scale);
            double reserved = compact ? 43 * scale + label.statePill + label.bindWidth : 51 * scale + label.statePill;
            label.title = GlassPainter.fit(renderer, notice.name(), width - reserved, TITLE * textScale);
        }
        glass.card(renderer, x, y, width, height, scale, alpha, reflection, background);
        glass.accentBar(renderer, x, y, height, scale, alpha, accent);
        double pillHeight = pillHeight(renderer, scale, textScale);
        double topHeight = topHeight(renderer, scale, textScale);
        double topCenter = y + (compact ? height / 2 : 11 * scale + topHeight / 2);
        double stateX = x + width - 13 * scale - label.statePill;
        glass.pill(renderer, stateX, topCenter - pillHeight / 2, label.statePill, pillHeight, scale,
            glass.tint(accent, alpha * 0.16));
        renderer.text(notice.enabled() ? "ON" : "OFF", stateX + (label.statePill - label.stateWidth) / 2,
            GlassPainter.centered(renderer, topCenter, STATE * textScale), glass.tint(accent, alpha), false, STATE * textScale);
        renderer.text(label.title, x + 16 * scale, GlassPainter.centered(renderer, topCenter, TITLE * textScale),
            glass.tint(text, alpha), false, TITLE * textScale);
        double bindCenter = compact ? topCenter : y + 11 * scale + topHeight + 5 * scale + pillHeight / 2;
        if (!compact) {
            renderer.text(notice.enabled() ? "Activated" : "Deactivated", x + 16 * scale,
                GlassPainter.centered(renderer, bindCenter, SMALL * textScale), glass.tint(text, alpha * 0.58), false, SMALL * textScale);
        }
        double bindX = compact ? stateX - 6 * scale - label.bindWidth : x + width - label.bindWidth - 13 * scale;
        glass.pill(renderer, bindX, bindCenter - pillHeight / 2, label.bindWidth, pillHeight, scale,
            glass.white(alpha * 0.07));
        renderer.text(label.bind, bindX + 8 * scale, GlassPainter.centered(renderer, bindCenter, SMALL * textScale),
            glass.tint(text, alpha * 0.85), false, SMALL * textScale);
        renderer.quad(x + 13 * scale, y + height - 3 * scale, width - 26 * scale, scale, glass.tint(accent, alpha * 0.09));
        renderer.quad(x + 13 * scale, y + height - 3 * scale, (width - 26 * scale) * remaining,
            scale, glass.tint(accent, alpha * 0.55));
    }
}
