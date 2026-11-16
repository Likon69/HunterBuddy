package com.hunterbuddy.hud;

final class CoordsCompass {
    static final String[] NAMES = {
        "South (+Z)", "South-West (-X +Z)", "West (-X)", "North-West (-X -Z)",
        "North (-Z)", "North-East (+X -Z)", "East (+X)", "South-East (+X +Z)"
    };

    private CoordsCompass() {
    }

    static String name(float yaw) {
        double wrapped = ((yaw % 360) + 360) % 360;
        return NAMES[(int) ((wrapped + 22.5) / 45) % NAMES.length];
    }
}
