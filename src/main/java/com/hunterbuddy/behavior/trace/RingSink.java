package com.hunterbuddy.behavior.trace;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class RingSink implements TraceSink {
    private final int capacity;
    private final Deque<TraceEvent> events = new ArrayDeque<>();

    public RingSink() {
        this(512);
    }

    public RingSink(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("ring capacity must be positive");
        this.capacity = capacity;
    }

    @Override
    public void accept(TraceEvent event) {
        if (events.size() == capacity) events.removeFirst();
        events.addLast(event);
    }

    public List<TraceEvent> snapshot() {
        return List.copyOf(events);
    }
}
