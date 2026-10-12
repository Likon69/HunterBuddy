package com.hunterbuddy.behavior.resource;

public record LookIntent(float yaw, float pitch) {
    public LookIntent {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90) {
            throw new IllegalArgumentException("invalid visible look angles");
        }
    }
}
