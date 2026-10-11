package com.hunterbuddy.behavior.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Trace {
    private final List<TraceSink> sinks;
    private final List<TraceEvent> pending = new ArrayList<>();

    public Trace(TraceSink... sinks) {
        this.sinks = List.of(sinks);
    }

    public void emit(TraceEvent event) {
        pending.add(Objects.requireNonNull(event));
    }

    public void publish() {
        List<TraceEvent> batch = List.copyOf(pending);
        pending.clear();
        RuntimeException failure = null;
        for (TraceEvent event : batch) {
            for (TraceSink sink : sinks) {
                try {
                    sink.accept(event);
                } catch (RuntimeException ex) {
                    if (failure == null) failure = ex;
                    else if (failure != ex) failure.addSuppressed(ex);
                }
            }
        }
        if (failure != null) throw failure;
    }
}
