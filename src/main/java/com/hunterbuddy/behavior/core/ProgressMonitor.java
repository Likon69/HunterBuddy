package com.hunterbuddy.behavior.core;

public final class ProgressMonitor {
    private final ProgressPolicy policy;
    private long inactiveMs;
    private long setbacks;
    private long invalidUntilTick;

    public ProgressMonitor(ProgressPolicy policy) {
        this.policy = policy;
    }

    public void sample(Clock clock, Sensors sensors) {
        if (sensors.setbacks() > setbacks) invalidUntilTick = clock.tick() + 20;
        setbacks = sensors.setbacks();
        inactiveMs += clock.activeGapMs();
    }

    public boolean accept(long tick, ProgressEvidence evidence) {
        if (tick < invalidUntilTick || !policy.accepted().contains(evidence.kind())) return false;
        inactiveMs = 0;
        return true;
    }

    public boolean fired() {
        return inactiveMs >= policy.noProgressMs();
    }

    public long inactiveMs() {
        return inactiveMs;
    }

    public long remainingMs() {
        return Math.max(0, policy.noProgressMs() - inactiveMs);
    }
}
