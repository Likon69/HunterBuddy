package com.hunterbuddy.behavior.core;

public record TargetPoint(double x, double y, double z) {
    public TargetPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("non-finite target position");
        }
    }

    public double distance(double fromX, double fromY, double fromZ) {
        return Math.hypot(Math.hypot(x - fromX, z - fromZ), y - fromY);
    }
}
