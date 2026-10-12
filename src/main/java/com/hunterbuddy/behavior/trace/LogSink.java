package com.hunterbuddy.behavior.trace;

import java.util.Objects;
import java.util.function.Consumer;

public final class LogSink implements TraceSink {
    private final Consumer<String> log;

    public LogSink(Consumer<String> log) {
        this.log = Objects.requireNonNull(log);
    }

    @Override
    public void accept(TraceEvent event) {
        log.accept("[bt] tick=" + event.tick() + " nowMs=" + event.nowMs() + " hook=" + event.hook() + " owner=" + event.owner()
            + " path=" + event.nodePath() + " kind=" + event.kind() + " code=" + event.code()
            + " reason=" + event.reason() + " detail=" + event.detail());
    }
}
