package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.BehaviorEngine;
import com.hunterbuddy.behavior.core.BehaviorStatus;
import com.hunterbuddy.behavior.core.Clock;
import com.hunterbuddy.behavior.core.EngineSnapshot;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.trace.Kind;
import com.hunterbuddy.behavior.trace.LogSink;
import com.hunterbuddy.behavior.trace.TraceSink;
import java.util.ArrayList;
import java.util.List;

public final class EngineChecks {
    private final List<String> checks = new ArrayList<>();
    private final List<StopReport> reports = new ArrayList<>();
    private final long[] time = {0};

    private EngineChecks() {
    }

    public static List<String> run() {
        EngineChecks check = new EngineChecks();
        check.lifecycle();
        check.switching();
        check.faults();
        check.exhaustion();
        return List.copyOf(check.checks);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("engine check failed: " + name);
        checks.add(name);
    }

    private BehaviorEngine engine(TraceSink... sinks) {
        reports.clear();
        return new BehaviorEngine(new Clock(() -> time[0]), reports::add, message -> { }, sinks);
    }

    private void tick(BehaviorEngine engine) {
        time[0] += 50;
        engine.tick();
    }

    private void lifecycle() {
        BehaviorEngine engine = engine();
        require(engine.snapshot().state() == RootState.IDLE && engine.snapshot().leases() == 0,
            "new engine is idle with no lease");
        engine.stopBot(ExitReason.STOPPED, "unused");
        require(reports.isEmpty(), "stop before start produces no false notification");
        engine.startBot(new LabBot(LabScenario.WORK));
        require(engine.snapshot().state() == RootState.RUNNING && engine.snapshot().stop() == null,
            "start installs LabBot and opens a clean session");
        try {
            engine.startBot(new LabBot(LabScenario.FAULT));
            throw new IllegalStateException("duplicate start accepted");
        } catch (IllegalStateException ex) {
            require(ex.getMessage().contains("already running"), "duplicate start rejected before mutation");
        }
        tick(engine);
        EngineSnapshot first = engine.snapshot();
        require(first.hook().equals("BOT") && first.path().contains("simulated-WORK") && first.leases() == 1,
            "snapshot exposes active BOT leaf and its simulated lease");
        require(first.actionRemainingTicks() == 1_199 && first.noProgressRemainingMs() == 180_000,
            "snapshot exposes actual action and attempt deadlines");
        tick(engine);
        require(engine.snapshot().noProgressRemainingMs() == 179_950, "attempt deadline decreases with active time");
        for (int i = 2; i < 40; i++) tick(engine);
        EngineSnapshot success = engine.snapshot();
        require(success.status().completed() == 1 && success.evidence() == 1 && success.leases() == 0,
            "simulated milestone yields proof and releases task lease");
        require(success.noProgressRemainingMs() == 180_000, "accepted lab proof restores attempt deadline");
        tick(engine);
        require(engine.snapshot().leases() == 1, "successful repeating lab task starts a fresh bounded action");
        engine.stopBot(ExitReason.DEAD, "Player died (simulated)");
        EngineSnapshot stopped = engine.snapshot();
        require(stopped.state() == RootState.STOPPED && stopped.leases() == 0 && stopped.hook().equals("none")
            && stopped.actionRemainingTicks() == 0 && stopped.noProgressRemainingMs() == 0,
            "stop releases every lease and removes active bot hook");
        require(reports.size() == 1 && reports.getFirst().reason() == ExitReason.DEAD
            && reports.getFirst().detail().equals("Player died (simulated)") && reports.getFirst().owner().equals("bot:lab"),
            "stop notification preserves death reason detail and owner");
        engine.stopBot(ExitReason.STOPPED, "duplicate");
        tick(engine);
        require(reports.size() == 1 && engine.snapshot().stop() == reports.getFirst(), "duplicate stop never repeats or overwrites report");
        require(BehaviorStatus.lines(stopped).stream().anyMatch(line -> line.contains("leases=0"))
            && BehaviorStatus.lines(stopped).stream().anyMatch(line -> line.contains("Stopped [DEAD]")),
            "shared HUD and command status expose clean stop and precise reason");
        engine.startBot(new LabBot(LabScenario.WORK));
        require(engine.snapshot().evidence() == 0 && engine.snapshot().status().completed() == 0
            && engine.snapshot().stop() == null, "new session starts with fresh displayed lab state");
        engine.stopBot(ExitReason.STOPPED, "same");
        engine.startBot(new LabBot(LabScenario.WORK));
        engine.stopBot(ExitReason.STOPPED, "same");
        require(reports.size() == 3 && reports.get(1).equals(reports.get(2)),
            "equal report values from separate sessions are both announced");
    }

    private void switching() {
        BehaviorEngine engine = engine();
        engine.startBot(new LabBot(LabScenario.WORK));
        tick(engine);
        engine.switchBot(new LabBot(LabScenario.STALLED));
        require(engine.snapshot().status().task().equals("WORK") && engine.snapshot().leases() == 1 && reports.isEmpty(),
            "switch remains pending until tick boundary");
        tick(engine);
        EngineSnapshot switched = engine.snapshot();
        require(switched.state() == RootState.RUNNING && switched.status().task().equals("STALLED")
            && switched.leases() == 0 && switched.recoveryLevel() == 0 && switched.stop() == null,
            "switch cleans old bot before installing replacement with reset recovery");
        require(reports.size() == 1 && reports.getFirst().detail().equals("Switching to lab"),
            "switch reports old bot stop exactly once");
        require(switched.recent().stream().anyMatch(event -> event.kind() == Kind.BOT && event.reason().equals("switched")),
            "switch emits explicit BOT switched trace");
        tick(engine);
        require(engine.snapshot().path().contains("simulated-STALLED") && engine.snapshot().leases() == 1,
            "replacement takes its own lease on next tick");
        engine.switchBot(new LabBot(LabScenario.WORK));
        engine.stopBot(ExitReason.DISCONNECTED, "Game session ended");
        tick(engine);
        require(engine.snapshot().state() == RootState.STOPPED && engine.snapshot().leases() == 0
            && reports.size() == 2 && reports.getLast().reason() == ExitReason.DISCONNECTED,
            "explicit stop cancels pending switch and preserves disconnect reason");
        try {
            engine.switchBot(new LabBot(LabScenario.WORK));
            throw new IllegalStateException("switch while stopped accepted");
        } catch (IllegalStateException ex) {
            require(ex.getMessage().contains("not running"), "switch while stopped rejected");
        }
    }

    private void faults() {
        List<String> logs = new ArrayList<>();
        BehaviorEngine engine = engine(new LogSink(logs::add));
        engine.startBot(new LabBot(LabScenario.FAULT));
        engine.switchBot(new LabBot(LabScenario.WORK));
        tick(engine);
        require(engine.snapshot().state() == RootState.STOPPED && engine.snapshot().leases() == 0 && reports.size() == 1,
            "injected lab fault stops cleanly and cancels queued switch");
        require(reports.getFirst().reason() == ExitReason.FAULT
            && reports.getFirst().detail().equals("IllegalStateException: Simulated LabBot fault"),
            "fault notification retains exception details");
        require(logs.stream().anyMatch(line -> line.startsWith("[bt]") && line.contains("kind=FAULT")
            && line.contains("nowMs=") && line.contains("owner=")), "LogSink records structured FAULT line");
        tick(engine);
        require(reports.size() == 1 && engine.snapshot().state() == RootState.STOPPED,
            "stopped fault session never ticks or announces twice");
        boolean[] failed = {false};
        engine = engine(event -> {
            if (!failed[0] && event.kind() == Kind.START) {
                failed[0] = true;
                throw new IllegalStateException("injected sink fault");
            }
        });
        engine.startBot(new LabBot(LabScenario.WORK));
        tick(engine);
        require(reports.size() == 1 && reports.getFirst().reason() == ExitReason.FAULT
            && reports.getFirst().detail().contains("injected sink fault") && engine.snapshot().leases() == 0,
            "trace failure during work is contained with clean fault report");
        engine = engine(event -> {
            if (event.kind() == Kind.START || event.kind() == Kind.FAULT || event.kind() == Kind.HOOK) {
                if (event.tick() > 0) throw new IllegalStateException("persistent sink fault");
            }
        });
        engine.startBot(new LabBot(LabScenario.WORK));
        tick(engine);
        require(reports.size() == 1 && reports.getFirst().reason() == ExitReason.FAULT
            && engine.snapshot().leases() == 0 && engine.snapshot().hook().equals("none"),
            "persistent sink failure cannot prevent stop notification or lease cleanup");
        engine.stopBot(ExitReason.STOPPED, "duplicate");
        require(reports.size() == 1, "persistent sink fault stop remains idempotent");
        engine = engine(event -> {
            if (event.kind() == Kind.BOT) throw new IllegalStateException("start sink fault");
        });
        try {
            engine.startBot(new LabBot(LabScenario.WORK));
            throw new IllegalStateException("failed start accepted");
        } catch (IllegalStateException ex) {
            require(ex.getMessage().equals("start sink fault") && reports.size() == 1
                && engine.snapshot().state() == RootState.STOPPED && engine.snapshot().leases() == 0,
                "start publication failure rolls back and notifies FAULT before rejection");
        }
    }

    private void exhaustion() {
        BehaviorEngine engine = engine();
        engine.startBot(new LabBot(LabScenario.STALLED));
        for (int i = 0; i < 30_000 && engine.snapshot().state() == RootState.RUNNING; i++) tick(engine);
        require(engine.snapshot().state() == RootState.STOPPED && engine.snapshot().leases() == 0,
            "permanently stalled simulated task reaches bounded clean stop");
        require(reports.size() == 1 && !reports.getFirst().detail().isBlank(), "exhausted targets notify exactly once with reason");
        require(engine.snapshot().evidence() == 0, "stalled work and recovery never fabricate progress");
        require(engine.snapshot().recent().size() <= 5, "HUD snapshot keeps at most five recent events");
    }
}
