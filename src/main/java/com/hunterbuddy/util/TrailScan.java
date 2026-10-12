package com.hunterbuddy.util;

public final class TrailScan {
    public enum Mode { WEAVE, SPIN, HOLD }

    private boolean active;
    private double phase;
    private double target;

    public boolean active() {
        return active;
    }

    public void stop() {
        active = false;
    }

    public void reset() {
        active = false;
        phase = 0.0;
    }

    public double next(Mode mode, boolean baritone, double steady, double degPerTick, double startWidth,
                       double msSinceSteerChunk, double chunkTimeoutMs, double trailTimeoutMs) {
        if (!active) {
            active = true;
            phase = 0.0;
            target = steady;
        }
        Mode effective = baritone && mode == Mode.SPIN ? Mode.HOLD : mode;
        switch (effective) {
            case WEAVE -> {
                phase += degPerTick;
                double starved = Math.max(0.0, msSinceSteerChunk - chunkTimeoutMs);
                double window = Math.max(1000.0, trailTimeoutMs - chunkTimeoutMs);
                double width = startWidth + (90.0 - startWidth) * Math.min(1.0, starved / window);
                target = TrailHeading.wrap(steady + width * Math.sin(Math.toRadians(phase)));
            }
            case SPIN -> target = TrailHeading.wrap(target + degPerTick);
            case HOLD -> target = steady;
        }
        return target;
    }
}
