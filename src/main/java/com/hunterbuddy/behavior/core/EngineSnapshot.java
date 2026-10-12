package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.bot.BotStats;
import com.hunterbuddy.behavior.bot.BotStatus;
import com.hunterbuddy.behavior.routine.RoutineStatus;
import com.hunterbuddy.behavior.trace.TraceEvent;
import java.util.List;
import java.util.Objects;

public record EngineSnapshot(String bot, RootState state, String hook, String path, BotStatus status,
                             long noProgressRemainingMs, long noProgressWindowMs, int actionRemainingTicks,
                             int recoveryLevel, int leases, long evidence, double tickMs,
                             List<TraceEvent> recent, StopReport stop, List<RoutineStatus> routines, BotStats.Snapshot stats,
                             String pauseCause, List<String> gates, List<String> outputs) {
    public EngineSnapshot {
        recent = List.copyOf(recent);
        routines = List.copyOf(routines);
        Objects.requireNonNull(stats);
        Objects.requireNonNull(pauseCause);
        gates = List.copyOf(gates);
        outputs = List.copyOf(outputs);
    }
}
