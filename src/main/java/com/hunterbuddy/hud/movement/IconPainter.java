package com.hunterbuddy.hud.movement;

import com.hunterbuddy.hud.MovementStatusHud.Layout;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;

final class IconPainter implements EntryPainter {
    private static final double BASE_ICON_SIZE = 16.0;
    private static final double VERTICAL_GAP = 2.0;
    private static final double HORIZONTAL_GAP = 4.0;

    private final HudRenderer renderer;
    private final MovementStatusOptions options;

    IconPainter(HudRenderer renderer, MovementStatusOptions options) {
        this.renderer = renderer;
        this.options = options;
    }

    private double iconSize() {
        return BASE_ICON_SIZE * options.iconScale.get();
    }

    @Override
    public double width(StatusEntry entry) {
        return iconSize();
    }

    @Override
    public double height(StatusEntry entry) {
        return iconSize();
    }

    @Override
    public double gap(Layout layout) {
        return layout == Layout.Vertical ? VERTICAL_GAP : HORIZONTAL_GAP;
    }

    @Override
    public void draw(StatusEntry entry, double x, double y) {
        float scale = options.iconScale.get().floatValue();
        renderer.post(() -> renderer.item(entry.icon(), (int) x, (int) y, scale, true));
    }

    @Override
    public Size placeholder(Layout layout) {
        double size = iconSize();
        if (layout == Layout.Vertical) return new Size(size, (size + VERTICAL_GAP) * 2);
        return new Size((size + HORIZONTAL_GAP) * 2, size);
    }
}
