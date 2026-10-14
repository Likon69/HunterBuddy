package com.hunterbuddy.behavior.trace;

import com.hunterbuddy.behavior.core.FailureCode;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class LogSink implements TraceSink {
    private final Consumer<String> log;
    private final BooleanSupplier fullTrace;

    public LogSink(Consumer<String> log) {
        this(log, () -> false);
    }

    public LogSink(Consumer<String> log, BooleanSupplier fullTrace) {
        this.log = Objects.requireNonNull(log);
        this.fullTrace = Objects.requireNonNull(fullTrace);
    }

    @Override
    public void accept(TraceEvent event) {
        if (!fullTrace.getAsBoolean() && !important(event)) return;
        log.accept("[bt] tick=" + event.tick() + " nowMs=" + event.nowMs() + " hook=" + event.hook() + " owner=" + event.owner()
            + " path=" + event.nodePath() + " kind=" + event.kind() + " code=" + event.code()
            + " reason=" + event.reason() + " detail=" + event.detail());
    }

    private boolean important(TraceEvent event) {
        return switch (event.kind()) {
            case FAULT, MONITOR, RECOVERY, BOT -> true;
            case HOOK -> event.reason().equals(TraceReasons.REFUSED);
            case GATE -> event.nodePath().equals("root") && (event.reason().equals(TraceReasons.PAUSED) || event.reason().equals(TraceReasons.RESUMED))
                || event.reason().equals(TraceReasons.OVERDUE) || event.reason().equals(TraceReasons.FLEE);
            case FAILURE -> event.code() == FailureCode.REFUSED;
            case STOP -> event.nodePath().equals("root") && switch (event.reason()) {
                case TraceReasons.STOPPED, TraceReasons.DISCONNECTED, TraceReasons.DEAD, TraceReasons.WORLD_CHANGED, TraceReasons.FAULT -> true;
                default -> false;
            };
            default -> false;
        };
    }
}
