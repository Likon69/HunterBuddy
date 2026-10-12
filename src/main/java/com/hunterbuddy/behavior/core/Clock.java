package com.hunterbuddy.behavior.core;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class Clock {
    private final LongSupplier time;
    private long tick;
    private long nowMs;
    private long gapMs;
    private long activeMs;
    private long frozenMs;
    private boolean initialized;

    public Clock() {
        this(() -> System.nanoTime() / 1_000_000);
    }

    public Clock(LongSupplier time) {
        this.time = Objects.requireNonNull(time);
    }

    public void advance() {
        long next = time.getAsLong();
        if (initialized && next < nowMs) throw new IllegalStateException("non-monotone clock");
        gapMs = initialized ? next - nowMs : 0;
        if (gapMs > 2_000) frozenMs += gapMs;
        nowMs = next;
        initialized = true;
        tick++;
    }

    public void countActive() {
        activeMs += activeGapMs();
    }

    public long activeGapMs() {
        return gapMs > 2_000 ? 0 : gapMs;
    }

    public long tick() {
        return tick;
    }

    public long nowMs() {
        return nowMs;
    }

    public long activeMs() {
        return activeMs;
    }

    public long frozenMs() {
        return frozenMs;
    }
}
