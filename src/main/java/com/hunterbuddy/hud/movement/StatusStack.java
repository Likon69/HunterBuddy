package com.hunterbuddy.hud.movement;

import com.hunterbuddy.hud.MovementStatusHud.Layout;
import java.util.List;

final class StatusStack {
    private final EntryPainter painter;
    private final Layout layout;

    StatusStack(EntryPainter painter, Layout layout) {
        this.painter = painter;
        this.layout = layout;
    }

    Size draw(List<StatusEntry> entries, double x, double y) {
        double offset = 0.0;
        for (StatusEntry entry : entries) {
            painter.draw(entry, x + offsetX(offset), y + offsetY(offset));
            offset += advance(entry) + painter.gap(layout);
        }
        return extent(entries);
    }

    Size drawPreview(List<StatusEntry> entries, double x, double y) {
        Size trimmed = draw(entries, x, y);
        if (entries.isEmpty()) return trimmed;
        double gap = painter.gap(layout);
        if (layout == Layout.Vertical) return new Size(trimmed.width(), trimmed.height() + gap);
        return new Size(trimmed.width() + gap, trimmed.height());
    }

    Size placeholder() {
        return painter.placeholder(layout);
    }

    private double offsetX(double offset) {
        return layout == Layout.Vertical ? 0.0 : offset;
    }

    private double offsetY(double offset) {
        return layout == Layout.Vertical ? offset : 0.0;
    }

    private double advance(StatusEntry entry) {
        return layout == Layout.Vertical ? painter.height(entry) : painter.width(entry);
    }

    private Size extent(List<StatusEntry> entries) {
        if (entries.isEmpty()) return new Size(0.0, 0.0);
        double gaps = painter.gap(layout) * (entries.size() - 1);
        double widest = entries.stream().mapToDouble(painter::width).max().orElse(0.0);
        double tallest = entries.stream().mapToDouble(painter::height).max().orElse(0.0);
        double totalWidth = entries.stream().mapToDouble(painter::width).sum();
        double totalHeight = entries.stream().mapToDouble(painter::height).sum();
        if (layout == Layout.Vertical) return new Size(widest, totalHeight + gaps);
        return new Size(totalWidth + gaps, tallest);
    }
}
