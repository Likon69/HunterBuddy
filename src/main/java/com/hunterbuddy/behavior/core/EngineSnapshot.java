package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.bot.BotStatus;
import com.hunterbuddy.behavior.trace.TraceEvent;
import java.util.List;

public record EngineSnapshot(String bot, RootState state, String hook, String path, BotStatus status,
                             long noProgressRemainingMs, long noProgressWindowMs, int actionRemainingTicks,
                             int recoveryLevel, int leases, long evidence, double tickMs,
                             List<TraceEvent> recent, StopReport stop) {
    public EngineSnapshot {
        recent = List.copyOf(recent);
    }
}
