package com.hunterbuddy.chatdock;

public final class DockMenuLayout {
    public double x;
    public double y;
    public double width;
    public double height;
    public double rowHeight;
    public double padding;
    public boolean above;

    public void update(double bx, double by, double button, double sw, double sh, double width,
                       double rowHeight, int count, double scale, double progress) {
        padding = 5 * scale;
        this.width = Math.min(sw, width);
        this.rowHeight = rowHeight;
        height = count * rowHeight + padding * 2;
        x = Math.max(0, Math.min(sw - this.width, bx));
        above = by + button + 6 * scale + height > sh;
        double target = above ? by - height - 6 * scale : by + button + 6 * scale;
        y = Math.max(0, Math.min(Math.max(0, sh - height), target)) + (above ? 1 : -1) * 6 * scale * (1 - progress);
    }

    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public int hit(double px, double py, int count) {
        if (!contains(px, py) || px < x + padding || px >= x + width - padding) return -1;
        int row = (int) Math.floor((py - y - padding) / rowHeight);
        return row >= 0 && row < count ? row : -1;
    }
}
