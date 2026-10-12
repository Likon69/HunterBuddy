package com.hunterbuddy.behavior.mc;

import baritone.api.event.events.PathEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import java.util.concurrent.atomic.AtomicLong;

public final class BaritoneEvents implements AbstractGameEventListener {
    private final AtomicLong failures = new AtomicLong();
    private volatile String last = "none";

    @Override
    public void onPathEvent(PathEvent event) {
        last = event.name();
        if (event == PathEvent.CALC_FAILED || event == PathEvent.NEXT_CALC_FAILED) failures.incrementAndGet();
    }

    public long failures() {
        return failures.get();
    }

    public String last() {
        return last;
    }
}
