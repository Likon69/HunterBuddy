package com.hunterbuddy.behavior.resource;

public record GatePolicy(int movementGraceTicks, int inventoryGraceTicks, long pauseLimitMs) {
    public static final GatePolicy LEGACY = new GatePolicy(10, 20, 900_000);

    public GatePolicy {
        if (movementGraceTicks < 0 || inventoryGraceTicks < 0 || pauseLimitMs < 1) throw new IllegalArgumentException("invalid gate policy");
    }
}
