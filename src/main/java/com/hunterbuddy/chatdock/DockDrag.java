package com.hunterbuddy.chatdock;

public final class DockDrag {
    public double x;
    public double y;
    public boolean active;
    public boolean moved;
    private double pointerX;
    private double pointerY;
    private double originX;
    private double originY;

    public void begin(double px, double py, double x, double y) {
        pointerX = px;
        pointerY = py;
        originX = this.x = x;
        originY = this.y = y;
        active = true;
        moved = false;
    }

    public boolean update(double px, double py, boolean pressed, double sw, double sh, double width, double height, double threshold) {
        if (!active) return false;
        double dx = px - pointerX;
        double dy = py - pointerY;
        if (dx * dx + dy * dy > threshold * threshold) moved = true;
        if (moved) {
            x = Math.max(0, Math.min(Math.max(0, sw - width), originX + dx));
            y = Math.max(0, Math.min(Math.max(0, sh - height), originY + dy));
        }
        if (pressed) return false;
        active = false;
        return true;
    }

    public void cancel() {
        active = moved = false;
    }
}
