package com.hunterbuddy.modules.flowesp;

final class FrameClock {
    private static final double MAX_DELTA_SECONDS = 0.1;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private long lastFrameNanos;

    double nextDeltaSeconds() {
        long now = System.nanoTime();
        double delta = lastFrameNanos == 0 ? 0.0 : (now - lastFrameNanos) / NANOS_PER_SECOND;
        lastFrameNanos = now;
        return Math.max(0.0, Math.min(MAX_DELTA_SECONDS, delta));
    }

    double seconds() {
        return System.nanoTime() / NANOS_PER_SECOND;
    }
}
