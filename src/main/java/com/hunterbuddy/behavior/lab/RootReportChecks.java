package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.ProgressPolicy;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.core.TreeRoot;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RootReportChecks {
    private final List<String> checks = new ArrayList<>();

    private RootReportChecks() {
    }

    public static List<String> run() {
        RootReportChecks check = new RootReportChecks();
        check.stops();
        check.faults();
        check.restartLimit();
        check.deadline();
        check.proofAndGap();
        return List.copyOf(check.checks);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("root report check failed: " + name);
        checks.add(name);
    }

    private TreeRoot root(Simulation sim, SimulatedAction action) {
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalStateException(message); });
        Timeout bounded = new Timeout(2_000, action);
        Guard guard = new Guard(new Condition("always", sensors -> true), bounded);
        hooks.insert(new HookEntry(Hook.BOT, "bot:lab", Contributor.BOT, 0, guard, true, bounded));
        hooks.applyPendingChanges(sim.ctx());
        return new TreeRoot(sim.ctx(), hooks,
            new Recovery(new Fail(FailureCode.NO_OPTION, "none"), new Succeed(), message -> { }));
    }

    private Simulation simulation() {
        return new Simulation(new ProgressPolicy(Set.of(EvidenceKind.MILESTONE), 100));
    }

    private void stops() {
        Simulation sim = simulation();
        TreeRoot root = root(sim, new SimulatedAction("stop-request", ctx -> {
            ctx.stopBot("mining: no remaining target");
            return Running.WORKING;
        }));
        require(root.stopReport().isEmpty(), "new root has no stop report");
        root.start();
        sim.tick(root, SimulatedSensors.idle());
        StopReport report = root.stopReport().orElseThrow();
        require(root.state() == RootState.STOPPED && report.reason() == ExitReason.STOPPED
            && report.detail().equals("mining: no remaining target") && report.owner().equals("bot:lab"),
            "deferred bot stop preserves exact reason and owner");
        require(report.tick() == sim.ctx().tick() && report.nowMs() == sim.ctx().nowMs(), "stop report records actual stop clock");
        root.stop(ExitReason.STOPPED, "duplicate stop");
        require(root.stopReport().orElseThrow().equals(report), "duplicate stop never overwrites original reason");
        root.start();
        require(root.stopReport().isEmpty(), "new session clears old stop report");
        root.stop(ExitReason.DEAD, "Player died");
        require(root.stopReport().orElseThrow().reason() == ExitReason.DEAD
            && root.stopReport().orElseThrow().detail().equals("Player died"), "explicit exit reason preserved independently of detail");
    }

    private void faults() {
        Simulation sim = simulation();
        TreeRoot root = root(sim, new SimulatedAction("fault", ctx -> {
            throw new IllegalStateException("injected work fault");
        }));
        root.start();
        sim.tick(root, SimulatedSensors.idle());
        require(root.stopReport().orElseThrow().reason() == ExitReason.FAULT
            && root.stopReport().orElseThrow().detail().equals("IllegalStateException: injected work fault"),
            "work fault yields meaningful stop report");

        sim = simulation();
        root = root(sim, new SimulatedAction("cleanup-fault", ctx -> {
            ctx.stopBot("requested stop");
            return Running.WORKING;
        }, (ctx, reason) -> { throw new IllegalStateException("cleanup fault"); }));
        root.start();
        sim.tick(root, SimulatedSensors.idle());
        require(root.state() == RootState.STOPPED && root.stopReport().orElseThrow().reason() == ExitReason.FAULT
            && root.stopReport().orElseThrow().detail().contains("cleanup fault"),
            "deferred cleanup failure upgrades stop report to FAULT");
    }

    private void restartLimit() {
        Simulation sim = simulation();
        TreeRoot root = root(sim, new SimulatedAction("stalled"));
        root.start();
        for (int i = 0; i < 3_000 && root.state() != RootState.STOPPED; i++) sim.tick(root, SimulatedSensors.idle());
        require(root.state() == RootState.STOPPED && root.stopReport().orElseThrow().detail().equals("automatic restart limit reached"),
            "SafeIdle restart exhaustion preserves stop reason");
    }

    private void deadline() {
        Simulation sim = simulation();
        TreeRoot root = root(sim, new SimulatedAction("stalled"));
        require(root.noProgressRemainingMs() == 0, "idle root has no active attempt deadline");
        root.start();
        require(root.noProgressRemainingMs() == 100, "start exposes configured attempt window");
        sim.tick(root, SimulatedSensors.idle());
        sim.tick(root, SimulatedSensors.idle());
        require(root.noProgressRemainingMs() == 50, "attempt deadline counts active elapsed time");
        sim.tick(root, SimulatedSensors.idle());
        require(root.recoveryLevel() == 1 && root.noProgressRemainingMs() == 100
            && sim.ctx().monitor().remainingMs() == 0 && sim.ctx().monitor().inactiveMs() == 100,
            "recovery exposes fresh attempt deadline without erasing expired inactivity");
        sim.tick(root, SimulatedSensors.idle());
        require(root.noProgressRemainingMs() == 50, "resumed attempt deadline decreases normally");
        sim.tick(root, SimulatedSensors.idle());
        require(root.recoveryLevel() == 3 && root.noProgressRemainingMs() == 0, "recovery in progress exposes no BOT attempt deadline");
        sim.tick(root, SimulatedSensors.idle());
        require(root.noProgressRemainingMs() == 100 && sim.ctx().evidenceSequence() == 0,
            "level three exposes fresh attempt deadline with no fabricated proof");
        root.stop(ExitReason.STOPPED);
        require(root.noProgressRemainingMs() == 0, "stopped root has no active deadline");
        root.start();
        require(root.noProgressRemainingMs() == 100, "restart deadline ignores accumulated earlier inactivity");
        root.stop(ExitReason.STOPPED);
    }

    private void proofAndGap() {
        Simulation sim = simulation();
        boolean[] proof = {false};
        TreeRoot root = root(sim, new SimulatedAction("proof", ctx -> {
            if (proof[0]) ctx.progress("confirmed milestone");
            return Running.WORKING;
        }));
        root.start();
        for (int i = 0; i < 3; i++) sim.tick(root, SimulatedSensors.idle());
        proof[0] = true;
        sim.tick(root, SimulatedSensors.idle());
        require(root.noProgressRemainingMs() == 100 && sim.ctx().monitor().inactiveMs() == 0
            && sim.ctx().evidenceSequence() == 1, "accepted proof restores actual progress window");
        proof[0] = false;
        sim.tick(root, SimulatedSensors.idle());
        sim.advance(10_000);
        root.tick(SimulatedSensors.idle());
        require(root.noProgressRemainingMs() == 50, "large credited clock gap does not consume attempt deadline");
        root.stop(ExitReason.STOPPED);
    }
}
