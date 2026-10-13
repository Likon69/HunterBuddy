package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.bot.BotStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BehaviorStatus {
    private BehaviorStatus() {
    }

    public static List<String> lines(EngineSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add("TreeJava: " + snapshot.bot() + " " + snapshot.state() + " (" + mode(snapshot) + ")");
        lines.add("Hook: " + snapshot.hook() + " | " + snapshot.status().task() + " | target=" + snapshot.status().target()
            + " | steps=" + snapshot.status().completed());
        lines.add("Path: " + snapshot.path());
        if (snapshot.state() == RootState.PAUSED || snapshot.state() == RootState.SUSPENDED) lines.add(snapshot.state() + ": " + snapshot.pauseCause());
        lines.addAll(snapshot.gates());
        snapshot.outputs().stream().filter(line -> line.contains("QUARANTINED") || line.contains("RETURNING") || line.contains("REVOKING")).forEach(lines::add);
        lines.add(String.format(Locale.ROOT, "No progress: %.1fs/%.1fs | action: %dt | recovery=%d | leases=%d",
            snapshot.noProgressRemainingMs() / 1_000.0, snapshot.noProgressWindowMs() / 1_000.0,
            snapshot.actionRemainingTicks(), snapshot.recoveryLevel(), snapshot.leases()));
        lines.add(String.format(Locale.ROOT, "Tick: %.3fms | %s proofs=%d", snapshot.tickMs(), mode(snapshot), snapshot.evidence()));
        lines.add(statsLine("Session", snapshot.stats().session()));
        if (snapshot.stop() != null) lines.add("Stopped [" + snapshot.stop().reason() + "]: " + snapshot.stop().detail());
        for (var routine : snapshot.routines()) lines.add("Routine " + routine.name() + ": " + routine.state() + " | " + routine.detail());
        for (var event : snapshot.recent()) lines.add(event.kind() + " " + event.reason());
        return List.copyOf(lines);
    }

    public static List<String> statsLines(EngineSnapshot snapshot) {
        BotStats.Values session = snapshot.stats().session();
        BotStats.Values cumulative = snapshot.stats().cumulative();
        return List.of("Stats: " + snapshot.bot() + " (" + mode(snapshot) + ")", statsLine("Session", session),
            "Session hooks (ms): " + session.hookMs(), "Session proofs: " + session.proofs(),
            "Session gains: " + session.gains(), statsLine("Cumulative", cumulative),
            "Cumulative hooks (ms): " + cumulative.hookMs(), "Cumulative proofs: " + cumulative.proofs(),
            "Cumulative gains: " + cumulative.gains(), "Session metrics: " + session.metrics(), "Cumulative metrics: " + cumulative.metrics());
    }

    private static String statsLine(String label, BotStats.Values stats) {
        return String.format(Locale.ROOT, "%s: active=%.1fs | proofs=%d | blocks=%.0f | items=%.0f | tx=%.0f | distance=%.1f | deaths=%d | faults=%d",
            label, stats.activeMs() / 1_000.0, stats.proofCount(), stats.metrics().getOrDefault("blocksMined", 0.0),
            stats.metrics().getOrDefault("pickups", 0.0), stats.metrics().getOrDefault("transactions", 0.0),
            stats.metrics().getOrDefault("distance", 0.0), stats.deaths(), stats.faults());
    }

    private static String mode(EngineSnapshot snapshot) {
        return snapshot.bot().equals("none") || snapshot.status().target().startsWith("simulated:") ? "simulated" : "live";
    }
}
