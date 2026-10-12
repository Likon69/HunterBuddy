package com.hunterbuddy.behavior.core;

public record UtilityFailure(int consecutive, long retryAtMs) {
    public static final UtilityFailure NONE = new UtilityFailure(0, 0);

    public boolean abandoned() {
        return consecutive >= 3;
    }

    public boolean waiting(long nowMs) {
        return !abandoned() && nowMs < retryAtMs;
    }
}
