package com.hunterbuddy.render;

public enum SkyboxStyle {
    AURORA(0, "Aurora"),
    NEBULA(1, "Nebula"),
    TWILIGHT(2, "Twilight"),
    STORM_NIGHT(3, "Storm Night"),
    DEEP_VOID(4, "Deep Void");

    private final int shaderId;
    private final String displayName;

    SkyboxStyle(int shaderId, String displayName) {
        this.shaderId = shaderId;
        this.displayName = displayName;
    }

    public int shaderId() {
        return shaderId;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
