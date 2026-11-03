package com.hunterbuddy.hud.movement;

import com.hunterbuddy.hud.MovementStatusHud.Layout;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;

final class TextPainter implements EntryPainter {
    private static final double VERTICAL_GAP = 2.0;
    private static final double HORIZONTAL_GAP = 8.0;
    private static final String PLACEHOLDER_SEPARATOR = "  ";

    private final HudRenderer renderer;
    private final MovementStatusOptions options;

    TextPainter(HudRenderer renderer, MovementStatusOptions options) {
        this.renderer = renderer;
        this.options = options;
    }

    @Override
    public double width(StatusEntry entry) {
        return textWidth(entry.text());
    }

    @Override
    public double height(StatusEntry entry) {
        return lineHeight();
    }

    @Override
    public double gap(Layout layout) {
        return layout == Layout.Vertical ? VERTICAL_GAP : HORIZONTAL_GAP;
    }

    @Override
    public void draw(StatusEntry entry, double x, double y) {
        renderer.text(entry.text(), x, y, entry.color(), options.textShadow.get(), options.textScale.get());
    }

    @Override
    public Size placeholder(Layout layout) {
        String sneaking = options.sneakingText.get();
        String sprinting = options.sprintingText.get();
        if (layout == Layout.Vertical) {
            return new Size(Math.max(textWidth(sneaking), textWidth(sprinting)), (lineHeight() + VERTICAL_GAP) * 2);
        }
        return new Size(textWidth(sneaking + PLACEHOLDER_SEPARATOR + sprinting), lineHeight());
    }

    private double textWidth(String text) {
        return renderer.textWidth(text, options.textShadow.get(), options.textScale.get());
    }

    private double lineHeight() {
        return renderer.textHeight(options.textShadow.get(), options.textScale.get());
    }
}
