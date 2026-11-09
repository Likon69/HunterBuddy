package com.hunterbuddy.hud.toast;

import java.util.Arrays;

public final class ModuleToastHistory {
    private final ModuleToastTimeline.Notice[] history = new ModuleToastTimeline.Notice[64];
    private long sequence;
    private long session;
    private long start;

    public synchronized void record(ModuleToastTimeline.Notice notice) {
        history[(int) (sequence++ % history.length)] = notice;
    }

    public synchronized long latest() {
        return sequence;
    }

    public synchronized long session() {
        return session;
    }

    public synchronized long start() {
        return start;
    }

    public synchronized ModuleToastTimeline.Notice get(long index) {
        if (index <= start || index > sequence || index <= sequence - history.length) return null;
        return history[(int) ((index - 1) % history.length)];
    }

    public synchronized void reset() {
        Arrays.fill(history, null);
        session++;
        start = sequence;
    }
}
