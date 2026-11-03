package com.hunterbuddy.modules.regear.arealoader;

public enum Heading {
    SOUTH(0, 1),
    WEST(-1, 0),
    NORTH(0, -1),
    EAST(1, 0);

    private final int stepX;
    private final int stepZ;

    Heading(int stepX, int stepZ) {
        this.stepX = stepX;
        this.stepZ = stepZ;
    }

    public int stepX() {
        return stepX;
    }

    public int stepZ() {
        return stepZ;
    }

    public boolean alongX() {
        return stepX != 0;
    }

    public static float normalize(float yaw) {
        return ((yaw % 360.0f) + 360.0f) % 360.0f;
    }

    public static Heading of(float yaw) {
        float angle = normalize(yaw);
        if (angle >= 315.0f || angle < 45.0f) return SOUTH;
        if (angle < 135.0f) return WEST;
        if (angle < 225.0f) return NORTH;
        return EAST;
    }
}
