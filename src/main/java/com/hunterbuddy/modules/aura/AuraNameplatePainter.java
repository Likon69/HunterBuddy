package com.hunterbuddy.modules.aura;

import com.hunterbuddy.hud.text.HudText;
import com.hunterbuddy.hud.text.HudTextScale;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

final class AuraNameplatePainter {
    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color BLACK = new Color(0, 0, 0);
    private final Color ink = new Color();
    private final Color shadow = new Color();

    void draw(HudRenderer renderer, AuraTrack track, double x, double y, double width, double size, double alpha, long now,
              Color fill, Color trail, Color frame, Color background) {
        double thickness = Math.max(1, 1.2 * size);
        double gap = Math.max(3, 3 * size);
        double boxWidth = Math.max(22 * size, track.lifeWidth + 8);
        double barWidth = width - boxWidth - gap;
        double barHeight = 10 * size;
        double barY = y + 18;
        double boxHeight = Math.max(barHeight + 4, HudText.textHeight(renderer, false, HudTextScale.SMALL) + 4);
        double boxY = barY + (barHeight - boxHeight) / 2;
        text(renderer, track.displayName, x + (barWidth - track.nameWidth) / 2, y, tint(WHITE, alpha), HudTextScale.NORMAL);
        rectangle(renderer, x - 1, barY - 1, barWidth + 2, barHeight + 2, tint(BLACK, alpha * 0.9));
        rectangle(renderer, x, barY, barWidth, barHeight, tint(frame, alpha));
        double innerWidth = Math.max(0, barWidth - thickness * 2);
        double innerHeight = Math.max(0, barHeight - thickness * 2);
        rectangle(renderer, x + thickness, barY + thickness, innerWidth, innerHeight, tint(background, alpha));
        rectangle(renderer, x + thickness, barY + thickness, innerWidth * track.health.trailFraction(now), innerHeight, tint(trail, alpha));
        rectangle(renderer, x + thickness, barY + thickness, innerWidth * track.health.fraction(), innerHeight, tint(fill, alpha));
        double boxX = x + barWidth + gap;
        rectangle(renderer, boxX - 1, boxY - 1, boxWidth + 2, boxHeight + 2, tint(BLACK, alpha * 0.9));
        rectangle(renderer, boxX, boxY, boxWidth, boxHeight, tint(frame, alpha));
        rectangle(renderer, boxX + thickness, boxY + thickness, boxWidth - thickness * 2, boxHeight - thickness * 2, tint(background, alpha));
        text(renderer, track.life, boxX + (boxWidth - track.lifeWidth) / 2,
            boxY + (boxHeight - HudText.textHeight(renderer, false, HudTextScale.SMALL)) / 2, tint(WHITE, alpha), HudTextScale.SMALL);
        if (!track.gold.isEmpty()) text(renderer, track.gold, x + (barWidth - track.goldWidth) / 2,
            barY + barHeight + 4, tint(frame, alpha), HudTextScale.SMALL);
    }

    void text(HudRenderer renderer, String value, double x, double y, Color color, double scale) {
        shadow.set(0, 0, 0, (int) (color.a * 0.9));
        HudText.text(renderer, value, x - 1, y, shadow, false, scale);
        HudText.text(renderer, value, x + 1, y, shadow, false, scale);
        HudText.text(renderer, value, x, y - 1, shadow, false, scale);
        HudText.text(renderer, value, x, y + 1, shadow, false, scale);
        HudText.text(renderer, value, x, y, color, false, scale);
    }

    private Color tint(Color color, double alpha) {
        ink.set(color.r, color.g, color.b, (int) (color.a * Math.clamp(alpha, 0, 1)));
        return ink;
    }

    private static void rectangle(HudRenderer renderer, double x, double y, double width, double height, Color color) {
        if (width > 0 && height > 0) renderer.quad(x, y, width, height, color);
    }
}
