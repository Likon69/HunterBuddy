package com.hunterbuddy.behavior.core;

public final class ProgressMonitor {
    private final ProgressPolicy policy;
    private long inactiveMs;
    private long setbacks;
    private long invalidUntilTick;
    private long activeMs;
    private long firstProgressAt = -1;

    public ProgressMonitor(ProgressPolicy policy) {
        this.policy = policy;
    }

    public void sample(Clock clock, Sensors sensors) {
        sample(clock, sensors, true);
    }

    public void sample(Clock clock, Sensors sensors, boolean countInactivity) {
        if (sensors.setbacks() > setbacks) invalidUntilTick = clock.tick() + 20;
        setbacks = sensors.setbacks();
        if (countInactivity) inactiveMs += clock.activeGapMs();
        activeMs = clock.activeMs();
        if (inactiveMs >= policy.noProgressMs()) firstProgressAt = -1;
    }

    public boolean accept(long tick, ProgressEvidence evidence) {
        if (!samplesValid(tick) || !policy.accepted().contains(evidence.kind())) return false;
        inactiveMs = 0;
        if (firstProgressAt < 0) firstProgressAt = activeMs;
        return true;
    }

    public boolean fired() {
        return inactiveMs >= policy.noProgressMs();
    }

    public boolean samplesValid(long tick) {
        return tick >= invalidUntilTick;
    }

    public long inactiveMs() {
        return inactiveMs;
    }

    public long remainingMs() {
        return Math.max(0, policy.noProgressMs() - inactiveMs);
    }

    public long windowMs() {
        return policy.noProgressMs();
    }

    public boolean sustainedFor(long ms) {
        return firstProgressAt >= 0 && activeMs - firstProgressAt >= ms && inactiveMs < policy.noProgressMs();
    }
}
