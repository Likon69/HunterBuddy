package com.hunterbuddy.hud.text;

public final class HudTextScale {
    public static final double SMALL = 0.65;
    public static final double NORMAL = 0.85;
    public static final double TITLE = 1.0;

    private HudTextScale() { }

    public static double fixed(double requested) {
        if (requested < 0) return NORMAL;
        if (requested < (SMALL + NORMAL) / 2) return SMALL;
        return requested < (NORMAL + TITLE) / 2 ? NORMAL : TITLE;
    }
}
