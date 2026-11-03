package com.hunterbuddy.modules.regear.arealoader;

public final class AutosaveTimer {
    private static final long PERIOD_NANOS = 600_000_000_000L;

    private long startedAt = System.nanoTime();

    public void restart() {
        startedAt = System.nanoTime();
    }

    public boolean isDue() {
        if (System.nanoTime() - startedAt <= PERIOD_NANOS) return false;
        restart();
        return true;
    }
}
