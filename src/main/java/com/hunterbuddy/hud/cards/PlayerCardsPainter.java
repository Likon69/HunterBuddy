package com.hunterbuddy.hud.cards;

import com.hunterbuddy.hud.glass.GlassPainter;
import com.hunterbuddy.hud.toast.ModuleToastBlur;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.item.ItemStack;

public final class PlayerCardsPainter {
    private static final double NAME = 0.88;
    private static final double VALUE = 0.88;
    private static final double PILL = 0.65;
    private static final String NAME_SAMPLE = "WWWWWW";
    private static final String DISTANCE_SAMPLE = "8888 b";
    private final GlassPainter glass = new GlassPainter();

    public CardMetrics measure(HudRenderer renderer, CardStyle style) {
        double s = style.scale();
        double t = style.textScale();
        double nameHeight = renderer.textHeight(false, NAME * t);
        double pillHeight = renderer.textHeight(false, PILL * t) + 6 * s;
        double nameWidth = renderer.textWidth(NAME_SAMPLE, false, NAME * t);
        double padding = (style.compact() ? 7 : 9) * s;
        double firstRow;
        double secondRow;
        double pillWidth;
        double rightWidth;
        double leftWidth;
        if (style.compact()) {
            firstRow = Math.max(Math.max(nameHeight, pillHeight), 16 * s);
            secondRow = 0;
            pillWidth = pillWidth(renderer, DISTANCE_SAMPLE, s, t);
            rightWidth = pillWidth;
            leftWidth = nameWidth;
        } else {
            firstRow = Math.max(nameHeight, renderer.textHeight(false, VALUE * t));
            secondRow = Math.max(16 * s, pillHeight);
            pillWidth = Math.max(Math.max(pillWidth(renderer, PlayerMovement.APPROACH.label(), s, t),
                pillWidth(renderer, PlayerMovement.RECEDE.label(), s, t)),
                Math.max(pillWidth(renderer, PlayerMovement.HOLD.label(), s, t),
                    pillWidth(renderer, PlayerMovement.GONE.label(), s, t)));
            rightWidth = Math.max(pillWidth, renderer.textWidth(DISTANCE_SAMPLE, false, VALUE * t));
            leftWidth = Math.max(nameWidth, PlayerTrack.GEAR_SLOTS.length * 16 * s + (PlayerTrack.GEAR_SLOTS.length - 1) * 2 * s);
        }
        double content = firstRow + (style.compact() ? 0 : 4 * s + secondRow);
        double minWidth = 12 * s + content + 8 * s + leftWidth + 8 * s + rightWidth + 12 * s;
        return new CardMetrics(content + 2 * padding, padding, content, firstRow, secondRow, pillHeight, pillWidth,
            rightWidth, minWidth);
    }

    public void draw(HudRenderer renderer, CardStyle style, CardMetrics metrics, PlayerCard card, double x, double y,
                     double width) {
        double s = style.scale();
        double t = style.textScale();
        double alpha = card.alpha();
        PlayerTrack track = card.track();
        if (style.blur()) ModuleToastBlur.INSTANCE.draw(x, y, width, metrics.height(), GlassPainter.radius(s), alpha);
        glass.card(renderer, x, y, width, metrics.height(), s, alpha, style.reflection(), style.background());
        glass.accentBar(renderer, x, y, metrics.height(), s, alpha, card.accent());
        double top = y + metrics.padding();
        double left = x + 12 * s;
        double right = x + width - 12 * s;
        double textX = left + metrics.headSize() + 8 * s;
        double firstCenter = top + metrics.firstRow() / 2;
        String distance = Math.round(track.distance()) + " b";
        CardIcons.head(renderer, track.skin(), left, top, metrics.headSize(), alpha);
        String name = GlassPainter.fit(renderer, track.name(), right - metrics.rightWidth() - 8 * s - textX, NAME * t);
        renderer.text(name, textX, GlassPainter.centered(renderer, firstCenter, NAME * t),
            glass.tint(style.text(), alpha), false, NAME * t);
        if (style.compact()) {
            pill(renderer, distance, right, firstCenter, metrics, card.pill(), alpha, s, t);
            return;
        }
        double valueWidth = renderer.textWidth(distance, false, VALUE * t);
        renderer.text(distance, right - valueWidth, GlassPainter.centered(renderer, firstCenter, VALUE * t),
            glass.tint(style.text(), alpha * 0.85), false, VALUE * t);
        double secondCenter = top + metrics.firstRow() + 4 * s + metrics.secondRow() / 2;
        pill(renderer, card.movement().label(), right, secondCenter, metrics, card.pill(), alpha, s, t);
        double gearX = textX;
        for (int i = 0; i < PlayerTrack.GEAR_SLOTS.length; i++) {
            ItemStack stack = track.gear(i);
            if (stack.isEmpty()) continue;
            CardIcons.item(renderer, stack, gearX, secondCenter - 8 * s, 16 * s);
            gearX += 18 * s;
        }
    }

    private void pill(HudRenderer renderer, String label, double right, double center, CardMetrics metrics,
                      Color color, double alpha, double scale, double textScale) {
        double left = right - metrics.pillWidth();
        glass.pill(renderer, left, center - metrics.pillHeight() / 2, metrics.pillWidth(), metrics.pillHeight(),
            scale, glass.tint(color, alpha * 0.16));
        double labelWidth = renderer.textWidth(label, false, PILL * textScale);
        renderer.text(label, left + (metrics.pillWidth() - labelWidth) / 2,
            GlassPainter.centered(renderer, center, PILL * textScale), glass.tint(color, alpha), false, PILL * textScale);
    }

    private static double pillWidth(HudRenderer renderer, String label, double scale, double textScale) {
        return Math.max(43 * scale, renderer.textWidth(label, false, PILL * textScale) + 14 * scale);
    }
}
