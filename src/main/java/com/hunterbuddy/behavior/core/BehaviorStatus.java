package com.hunterbuddy.behavior.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BehaviorStatus {
    private BehaviorStatus() {
    }

    public static List<String> lines(EngineSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add("TreeJava: " + snapshot.bot() + " " + snapshot.state() + " (simulated)");
        lines.add("Hook: " + snapshot.hook() + " | " + snapshot.status().task() + " | target=" + snapshot.status().target()
            + " | steps=" + snapshot.status().completed());
        lines.add("Path: " + snapshot.path());
        lines.add(String.format(Locale.ROOT, "No progress: %.1fs/%.1fs | action: %dt | recovery=%d | leases=%d",
            snapshot.noProgressRemainingMs() / 1_000.0, snapshot.noProgressWindowMs() / 1_000.0,
            snapshot.actionRemainingTicks(), snapshot.recoveryLevel(), snapshot.leases()));
        lines.add(String.format(Locale.ROOT, "Tick: %.3fms | simulated proofs=%d", snapshot.tickMs(), snapshot.evidence()));
        if (snapshot.stop() != null) lines.add("Stopped [" + snapshot.stop().reason() + "]: " + snapshot.stop().detail());
        for (var event : snapshot.recent()) lines.add(event.kind() + " " + event.reason());
        return List.copyOf(lines);
    }
}
