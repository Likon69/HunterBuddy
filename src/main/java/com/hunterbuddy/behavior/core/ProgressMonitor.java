package com.hunterbuddy.behavior.core;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class ProgressMonitor {
    private ProgressPolicy policy;
    private long inactiveMs;
    private long setbacks;
    private long invalidUntilTick;
    private long activeMs;
    private long firstProgressAt = -1;
    private BooleanSupplier sampleValidity;

    public ProgressMonitor(ProgressPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    public void configure(ProgressPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
        inactiveMs = 0;
        firstProgressAt = -1;
    }

    public void sample(Clock clock, Sensors sensors) {
        sample(clock, sensors, true);
    }

    public void sample(Clock clock, Sensors sensors, boolean countInactivity) {
        observeSetbacks(clock.tick(), sensors.setbacks());
        if (countInactivity) inactiveMs += clock.activeGapMs();
        activeMs = clock.activeMs();
        if (inactiveMs >= policy.noProgressMs()) firstProgressAt = -1;
    }

    void observeSetbacks(long tick, long count) {
        if (count > setbacks) invalidUntilTick = tick + 20;
        setbacks = count;
    }

    void sampleValidity(BooleanSupplier sampleValidity) {
        this.sampleValidity = sampleValidity;
    }

    void resetSamples() {
        setbacks = 0;
        invalidUntilTick = 0;
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
        return sampleValidity == null ? tick >= invalidUntilTick : sampleValidity.getAsBoolean();
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
