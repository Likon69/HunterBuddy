package com.hunterbuddy.hud;

import com.hunterbuddy.modules.messenger.ui.Painter;
import com.hunterbuddy.modules.messenger.ui.Rect;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

final class MessengerBadge {
    private Rect bounds = Rect.NONE;

    boolean contains(double x, double y) {
        if (!bounds.contains(x, y)) return false;
        double radius = bounds.h() / 2.0;
        double cx = Math.max(bounds.x() + radius, Math.min(x, bounds.x() + bounds.w() - radius));
        return Math.hypot(x - cx, y - bounds.y() - radius) <= radius;
    }

    void draw(HudRenderer renderer, int unread, double x, double y, double size, double textScale,
              boolean shadow, Color color, double grow) {
        bounds = Rect.NONE;
        if (unread <= 0) return;

        String label = unread > 99 ? "99+" : String.valueOf(unread);
        double height = size * 0.34;
        double scale = Math.min(textScale, height * 0.64 / renderer.textHeight(shadow, 1.0));
        scale = Math.min(scale, size * 0.46 / renderer.textWidth(label, shadow, 1.0));
        scale *= grow;
        double tw = renderer.textWidth(label, shadow, scale);
        double th = renderer.textHeight(shadow, scale);
        height *= grow;
        double width = Math.max(height, tw + height * 0.48);
        double left = x + size * 1.05 - width;
        double top = y - size * 0.05;
        bounds = new Rect(left, top, width, height);

        Painter p = new Painter(renderer, scale, shadow);
        p.round(left - 1.5, top - 1.5, width + 3.0, height + 3.0, height / 2.0 + 1.5, new Color(15, 22, 34, 245));
        p.round(left, top, width, height, height / 2.0, color);
        Color ink = color.r * 0.2126 + color.g * 0.7152 + color.b * 0.0722 > 175.0
            ? new Color(18, 24, 36, 255) : new Color(255, 255, 255, 255);
        renderer.text(label, left + (width - tw) / 2.0, top + (height - th) / 2.0, ink, shadow, scale);
    }
}
