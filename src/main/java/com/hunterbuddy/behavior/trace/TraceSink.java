package com.hunterbuddy.behavior.trace;

public interface TraceSink {
    void accept(TraceEvent event);
}
