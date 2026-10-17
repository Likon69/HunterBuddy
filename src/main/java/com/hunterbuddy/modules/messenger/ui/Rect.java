package com.hunterbuddy.modules.messenger.ui;

public record Rect(double x, double y, double w, double h) {
    public static final Rect NONE = new Rect(0, 0, 0, 0);

    public boolean contains(double px, double py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
