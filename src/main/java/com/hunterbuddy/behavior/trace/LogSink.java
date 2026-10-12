package com.hunterbuddy.behavior.trace;

import com.hunterbuddy.behavior.core.FailureCode;
import java.util.Objects;
import java.util.function.Consumer;

public final class LogSink implements TraceSink {
    private final Consumer<String> log;

    public LogSink(Consumer<String> log) {
        this.log = Objects.requireNonNull(log);
    }

    @Override
    public void accept(TraceEvent event) {
        if (!important(event)) return;
        log.accept("[bt] tick=" + event.tick() + " nowMs=" + event.nowMs() + " hook=" + event.hook() + " owner=" + event.owner()
            + " path=" + event.nodePath() + " kind=" + event.kind() + " code=" + event.code()
            + " reason=" + event.reason() + " detail=" + event.detail());
    }

    private boolean important(TraceEvent event) {
        return switch (event.kind()) {
            case FAULT, MONITOR, RECOVERY, BOT -> true;
            case HOOK -> event.reason().equals("refused");
            case FAILURE -> event.code() == FailureCode.REFUSED;
            case STOP -> event.nodePath().equals("root") && switch (event.reason()) {
                case "STOPPED", "DISCONNECTED", "DEAD", "WORLD_CHANGED", "FAULT" -> true;
                default -> false;
            };
            default -> false;
        };
    }
}
