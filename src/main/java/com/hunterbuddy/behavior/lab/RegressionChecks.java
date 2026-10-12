package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.Blackboard;
import com.hunterbuddy.behavior.core.Clock;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressPolicy;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.RestartBudget;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.TreeRoot;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.nodes.Budget;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.MarkFailed;
import com.hunterbuddy.behavior.nodes.ProgressGuard;
import com.hunterbuddy.behavior.nodes.SafeIdle;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.resource.ResourceArbiter;
import com.hunterbuddy.behavior.trace.Kind;
import com.hunterbuddy.behavior.trace.RingSink;
import com.hunterbuddy.behavior.trace.Trace;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RegressionChecks {
    private final List<String> checks = new ArrayList<>();

    private RegressionChecks() {
    }

    public static List<String> run() {
        RegressionChecks check = new RegressionChecks();
        check.progressMemory();
        check.guardEntry();
        check.hookMemory();
        check.faults();
        check.recoveryOwner();
        check.restarts();
        check.recoveryAttempts();
        check.recoveryProgress();
        return List.copyOf(check.checks);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("regression failed: " + name);
        checks.add(name);
    }

    private HookEntry entry(Hook hook, String owner, Contributor contributor, Guard guard) {
        return new HookEntry(hook, owner, contributor, 0, guard, true, guard.child());
    }

    private Guard always(Node child) {
        return new Guard(new Condition("always", sensors -> true), child);
    }

    private Recovery recovery() {
        return new Recovery(new Fail(FailureCode.NO_OPTION, "no alternative"), new Succeed(), message -> { });
    }

    private Node leased(Node child) {
        return new WithLease(Set.of(Resource.LOOK), LeasePolicy.WAIT, new Timeout(5_000, child));
    }

    private void progressMemory() {
        Simulation sim = new Simulation();
        ProgressGuard guard = new ProgressGuard(Sensors::x, 1, 20, new SimulatedAction("move")).keyedBy(ctx -> "target:a/x");
        sim.tick(guard, SimulatedSensors.position(0));
        sim.tick(guard, SimulatedSensors.position(1));
        require(sim.ctx().evidenceSequence() == 1, "first goal best is accepted");
        guard.halt(sim.ctx(), ExitReason.PREEMPTED);
        sim.tick(guard, SimulatedSensors.position(0));
        sim.tick(guard, SimulatedSensors.position(1));
        require(sim.ctx().evidenceSequence() == 1, "preemption never turns repeated best into progress");
        guard.halt(sim.ctx(), ExitReason.STOPPED);
        guard.resetMemory();
        ProgressGuard alternative = new ProgressGuard(Sensors::x, 1, 20, new SimulatedAction("alternative")).keyedBy(ctx -> "target:a/x");
        sim.tick(alternative, SimulatedSensors.position(0));
        sim.tick(alternative, SimulatedSensors.position(1));
        require(sim.ctx().evidenceSequence() == 1, "another strategy shares goal best without fake progress");
        sim.tick(alternative, SimulatedSensors.position(2));
        require(sim.ctx().evidenceSequence() == 2, "only a new goal best supplies evidence");
        alternative.halt(sim.ctx(), ExitReason.STOPPED);
        ProgressGuard newTarget = new ProgressGuard(Sensors::x, 1, 20, new SimulatedAction("new-target")).keyedBy(ctx -> "target:b/x");
        sim.tick(newTarget, SimulatedSensors.position(0));
        sim.tick(newTarget, SimulatedSensors.position(1));
        require(sim.ctx().evidenceSequence() == 3, "different target owns a distinct progress baseline");
        newTarget.halt(sim.ctx(), ExitReason.STOPPED);

        Simulation stalled = new Simulation();
        ProgressGuard waiting = new ProgressGuard(Sensors::x, 1, 3, new SimulatedAction("stall"));
        stalled.tick(waiting);
        waiting.halt(stalled.ctx(), ExitReason.PAUSED);
        stalled.tick(waiting);
        waiting.halt(stalled.ctx(), ExitReason.PREEMPTED);
        NodeResult result = stalled.tick(waiting);
        require(result instanceof Failure failure && failure.code() == FailureCode.NO_PROGRESS, "pause and preemption preserve progress window");

        Simulation setbacks = new Simulation();
        ProgressGuard position = new ProgressGuard(Sensors::x, 1, 100, new SimulatedAction("setback-position"));
        setbacks.tick(position, SimulatedSensors.position(-100, 1));
        for (int i = 0; i < 20; i++) setbacks.tick(position, SimulatedSensors.position(0, 1));
        require(setbacks.ctx().evidenceSequence() == 0, "setback sample cannot seed goal baseline");
        setbacks.tick(position, SimulatedSensors.position(1, 1));
        require(setbacks.ctx().evidenceSequence() == 1, "valid movement after setback provides goal evidence");
        position.halt(setbacks.ctx(), ExitReason.STOPPED);
    }

    private void guardEntry() {
        Simulation sim = new Simulation();
        SimulatedAction blocked = new SimulatedAction("held-false");
        Guard guard = new Guard(new Condition("never", sensors -> false), new Timeout(100, blocked)).hold(20);
        NodeResult result = sim.tick(guard);
        require(result instanceof Failure && blocked.starts() == 0, "hold never bypasses false entry condition outside selector");
        SimulatedAction hysteresis = new SimulatedAction("entry-false-stay-true");
        Guard asymmetric = new Guard(new Condition("entry", sensors -> false), new Timeout(100, hysteresis))
            .stayWhile(new Condition("stay", sensors -> true));
        result = sim.tick(asymmetric);
        require(result instanceof Failure && hysteresis.starts() == 0, "stayWhile never authorizes initial entry");
    }

    private void hookMemory() {
        Simulation sim = new Simulation();
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalStateException(message); });
        SimulatedAction urgent = new SimulatedAction("urgent", ctx -> new Failure(FailureCode.REFUSED, "test"));
        Guard emergency = always(new Timeout(1_000, urgent)).onFailureCooldown(200);
        hooks.insert(entry(Hook.SURVIVAL, "routine:survival", Contributor.ROUTINE, emergency));
        SimulatedAction task = new SimulatedAction("task");
        hooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(leased(task))));
        hooks.applyPendingChanges(sim.ctx());
        TreeRoot root = new TreeRoot(sim.ctx(), hooks, recovery());
        root.start();
        sim.tick(root, SimulatedSensors.idle());
        String path = task.path();
        hooks.insert(entry(Hook.BOT_PRE, "plugin:signal", Contributor.PLUGIN,
            new Guard(new Condition("never", sensors -> false), new Succeed())));
        sim.tick(root, SimulatedSensors.idle());
        for (int i = 0; i < 198; i++) sim.tick(root, SimulatedSensors.idle());
        require(urgent.starts() == 1 && task.starts() == 1 && task.path().equals(path), "rebuild preserves cooldown, active branch and stable path");
        sim.tick(root, SimulatedSensors.idle());
        require(urgent.starts() == 2, "cooldown expires at its original tick after rebuild");
        root.stop(ExitReason.STOPPED);

        Simulation paths = new Simulation();
        TreeHooks duplicate = new TreeHooks(message -> { throw new IllegalStateException(message); });
        Budget first = new Budget("attempt", 1, new Succeed());
        Budget second = new Budget("attempt", 1, new Succeed());
        duplicate.insert(entry(Hook.SESSION, "core:session", Contributor.CORE, new Guard(new Condition("state", Sensors::eating), first)));
        duplicate.insert(entry(Hook.SESSION, "core:session", Contributor.CORE, new Guard(new Condition("state", sensors -> !sensors.eating()), second)));
        duplicate.applyPendingChanges(paths.ctx());
        NodeResult idle = paths.tick(duplicate.build(), SimulatedSensors.idle());
        NodeResult eating = paths.tick(duplicate.build(), SimulatedSensors.capture(false, true, false, false, 0, 0));
        require(idle instanceof Success && eating instanceof Success && !first.path().equals(second.path()), "equal guard names have unique paths and independent budgets");
    }

    private void faults() {
        Simulation sim = new Simulation();
        TreeHooks hooks = new TreeHooks(message -> { });
        TreeRoot root = new TreeRoot(sim.ctx(), hooks, recovery());
        SimulatedAction fault = new SimulatedAction("queued-fault", ctx -> {
            root.start();
            hooks.insert(entry(Hook.BOT_PRE, "plugin:pending", Contributor.PLUGIN, always(new Succeed())));
            throw new IllegalStateException("injected");
        }, (ctx, reason) -> root.start());
        hooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(leased(fault))));
        hooks.applyPendingChanges(sim.ctx());
        root.start();
        sim.tick(root, SimulatedSensors.idle());
        require(root.state() == RootState.STOPPED && sim.ctx().leases().snapshot().isEmpty(), "FAULT dominates queued starts including cleanup requests");
        sim.tick(root, SimulatedSensors.idle());
        require(root.state() == RootState.STOPPED && hooks.entries().size() == 1 && fault.starts() == 1, "FAULT discards pending hooks and cannot restart on next tick");

        Clock clock = new Clock(() -> 0);
        RingSink ring = new RingSink();
        TickContext ctx = new TickContext(clock, new Blackboard(), new ResourceArbiter(),
            new Trace(ring, event -> { throw new IllegalStateException("sink failure"); }), ProgressPolicy.DEFAULT);
        TreeHooks traceHooks = new TreeHooks(message -> { });
        SimulatedAction traceTask = new SimulatedAction("trace-fault");
        traceHooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(leased(traceTask))));
        traceHooks.applyPendingChanges(ctx);
        TreeRoot traceRoot = new TreeRoot(ctx, traceHooks, recovery());
        traceRoot.start();
        clock.advance();
        traceRoot.tick(SimulatedSensors.idle());
        require(traceRoot.state() == RootState.STOPPED && !traceTask.active() && ctx.leases().snapshot().isEmpty(), "trace sink exception stops root and releases lease");
        require(ring.snapshot().stream().anyMatch(event -> event.kind() == Kind.FAULT), "healthy trace sink still receives FAULT from another sink");

        Simulation removal = new Simulation();
        TreeHooks removalHooks = new TreeHooks(message -> { });
        SimulatedAction retained = new SimulatedAction("retained-task");
        SimulatedAction throwing = new SimulatedAction("throwing-cleanup", current -> Running.WORKING,
            (current, reason) -> { throw new IllegalStateException("cleanup failure"); });
        removalHooks.insert(entry(Hook.SURVIVAL, "routine:survival", Contributor.ROUTINE,
            new Guard(new Condition("eating", Sensors::eating), leased(throwing))));
        removalHooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(leased(retained))));
        removalHooks.applyPendingChanges(removal.ctx());
        TreeRoot removalRoot = new TreeRoot(removal.ctx(), removalHooks, recovery());
        removalRoot.start();
        removal.tick(removalRoot, SimulatedSensors.idle());
        Sensors eating = SimulatedSensors.capture(false, true, false, false, 0, 0);
        removal.tick(removalRoot, eating);
        removalHooks.remove("routine:survival");
        removal.tick(removalRoot, eating);
        require(removalRoot.state() == RootState.STOPPED && removal.ctx().leases().snapshot().isEmpty()
            && removalHooks.entries().stream().noneMatch(installed -> installed.guard().active()),
            "cleanup failure during hook removal halts all registered branches");
        removalRoot.start();
        removal.tick(removalRoot, SimulatedSensors.idle());
        require(removalRoot.state() == RootState.RUNNING && retained.starts() == 2,
            "failed hook reconstruction preserves a complete tree for explicit restart");
        removalRoot.stop(ExitReason.STOPPED);
    }

    private void recoveryOwner() {
        Simulation sim = new Simulation(new ProgressPolicy(Set.of(EvidenceKind.MILESTONE), 100));
        TreeHooks hooks = new TreeHooks(message -> { });
        SimulatedAction task = new SimulatedAction("recover-task", ctx -> {
            ctx.bb().set(ctx.owner(), TickContext.CURRENT_TARGET, "point:a");
            return Running.WORKING;
        });
        hooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(leased(task))));
        hooks.applyPendingChanges(sim.ctx());
        sim.ctx().budgets().spend("bot:lab", "mission", "attempt", "point:a", 1);
        sim.ctx().budgets().spend("core", "mission", "attempt", "point:a", 1);
        Key<Integer> value = new Key<>("task-value", Integer.class, Scope.TASK, false);
        sim.ctx().bb().openTask("bot:lab");
        sim.ctx().bb().set("bot:lab", value, 7);
        boolean[] fallbackOwner = { false, false };
        Recovery recovery = new Recovery(
            new SimulatedAction("level-two", ctx -> {
                fallbackOwner[0] = ctx.owner().equals("bot:lab")
                    && ctx.bb().get(ctx.owner(), TickContext.CURRENT_TARGET).orElse("").equals("point:a");
                return new Failure(FailureCode.NO_OPTION, "no alternative");
            }),
            new SimulatedAction("level-three", ctx -> {
                fallbackOwner[1] = ctx.owner().equals("bot:lab")
                    && ctx.bb().get(ctx.owner(), MarkFailed.failedKey("point:a")).orElse(false);
                return Success.INSTANCE;
            }), message -> { });
        TreeRoot root = new TreeRoot(sim.ctx(), hooks, recovery);
        root.start();
        for (int i = 0; i < 3; i++) sim.tick(root, SimulatedSensors.idle());
        require(sim.ctx().bb().get("bot:lab", value).isEmpty(), "recovery level one clears bot TASK namespace");
        for (int i = 0; i < 5; i++) sim.tick(root, SimulatedSensors.idle());
        require(sim.ctx().budgets().spend("bot:lab", "mission", "attempt", "point:a", 1), "recovery level three resets active bot budgets");
        require(!sim.ctx().budgets().spend("core", "mission", "attempt", "point:a", 1), "bot recovery never resets unrelated core budgets");
        require(sim.ctx().bb().get("bot:lab", MarkFailed.failedKey("point:a")).orElse(false), "recovery marks current bot target failed");
        require(fallbackOwner[0] && fallbackOwner[1], "recovery alternatives read bot memory and level three sees failed target");
        root.stop(ExitReason.STOPPED);
        sim.ctx().bb().closeTask("bot:lab");
        sim.ctx().bb().removeOwner("bot:lab");
        sim.ctx().bb().restoreOwner("bot:lab");
        require(sim.ctx().bb().get("bot:lab", MarkFailed.failedKey("point:a")).orElse(false), "failed target survives durable owner save and restore");
    }

    private void restarts() {
        Simulation delay = new Simulation();
        SafeIdle idle = new SafeIdle(message -> { });
        NodeResult result = delay.tick(idle);
        for (int i = 0; i < 599; i++) result = delay.tick(idle);
        require(result instanceof Running, "SafeIdle waits the full 30 seconds");
        require(delay.tick(idle) instanceof Success, "SafeIdle completes after 30 seconds");

        RestartBudget budget = new RestartBudget();
        require(budget.tryRestart(0) && budget.tryRestart(10) && budget.tryRestart(20), "three automatic restarts allowed");
        require(!budget.tryRestart(30) && !budget.tryRestart(3_599_999), "fourth automatic restart blocked inside rolling hour");
        require(budget.tryRestart(3_600_000), "restart budget frees expired entry at one hour");

        Simulation sim = new Simulation(new ProgressPolicy(Set.of(EvidenceKind.MILESTONE), 100));
        List<String> alerts = new ArrayList<>();
        TreeHooks hooks = new TreeHooks(message -> { });
        SimulatedAction task = new SimulatedAction("restart-task");
        hooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(leased(task))));
        hooks.applyPendingChanges(sim.ctx());
        Recovery recovery = new Recovery(new Fail(FailureCode.NO_OPTION, "none"), new Succeed(), alerts::add);
        TreeRoot root = new TreeRoot(sim.ctx(), hooks, recovery);
        root.start();
        int observedRestarts = 0;
        int resumedTicks = 0;
        boolean awaitingResume = false;
        for (int i = 0; i < 3_000 && root.state() != RootState.STOPPED; i++) {
            sim.tick(root, SimulatedSensors.idle());
            if (awaitingResume) {
                require(hooks.build().successfulHook() == null && sim.ring().snapshot().stream()
                    .anyMatch(event -> event.tick() == sim.ctx().tick() && event.hook() == Hook.BOT && event.kind() == Kind.START),
                    "automatic restart " + observedRestarts + " executes BOT on next tick");
                resumedTicks++;
                awaitingResume = false;
            }
            if (recovery.restartCount() > observedRestarts) {
                observedRestarts = recovery.restartCount();
                awaitingResume = true;
            }
        }
        require(root.state() == RootState.STOPPED && recovery.restartCount() == 3,
            "SafeIdle restarts three times then StopBot ends root: state=" + root.state() + " restarts=" + recovery.restartCount());
        require(task.starts() <= 5, "automatic recovery preserves Action loop budget");
        require(resumedTicks == 3 && !awaitingResume, "all three automatic restarts actually resume BOT");
        require(sim.ctx().leases().snapshot().isEmpty() && alerts.size() == 2
            && root.stopReport().orElseThrow().detail().equals("automatic restart limit reached"),
            "restart exhaustion releases resources and reports stop separately from the two distinct idle periods: alerts=" + alerts.size());
        require(sim.ctx().evidenceSequence() == 0 && sim.ctx().monitor().inactiveMs() > 120_000, "recovery restarts never manufacture progress or erase inactivity");
        require(sim.ctx().monitor().fired() && sim.ctx().monitor().remainingMs() == 0, "recovery preserves expired monitor evidence state");
    }

    private void recoveryAttempts() {
        Simulation sim = new Simulation(new ProgressPolicy(Set.of(EvidenceKind.MILESTONE), 100));
        TreeHooks hooks = new TreeHooks(message -> { });
        SimulatedAction task = new SimulatedAction("resuming-task");
        hooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(leased(task))));
        hooks.applyPendingChanges(sim.ctx());
        Recovery recovery = new Recovery(new Succeed(), new Succeed(), message -> { });
        TreeRoot root = new TreeRoot(sim.ctx(), hooks, recovery);
        root.start();
        for (int i = 0; i < 3; i++) sim.tick(root, SimulatedSensors.idle());
        require(root.recoveryLevel() == 1 && task.starts() == 1, "first recovery follows expired window");
        sim.tick(root, SimulatedSensors.idle());
        require(task.starts() == 2 && root.recoveryLevel() == 1, "successful level one really resumes task");
        require(sim.ctx().monitor().fired() && sim.ctx().monitor().inactiveMs() == 150
            && sim.ctx().evidenceSequence() == 0, "resuming task preserves inactivity without fabricated evidence");
        sim.tick(root, SimulatedSensors.idle());
        require(root.recoveryLevel() == 2 && task.starts() == 2, "retry window remains bounded without progress");
        sim.tick(root, SimulatedSensors.idle());
        require(task.starts() == 3, "successful level two really resumes task");
        sim.tick(root, SimulatedSensors.idle());
        sim.tick(root, SimulatedSensors.idle());
        require(root.recoveryLevel() == 3 && task.starts() == 4, "successful level three really resumes task");
        root.stop(ExitReason.STOPPED);
        root.start();
        sim.tick(root, SimulatedSensors.idle());
        require(task.starts() == 5 && root.recoveryLevel() == 0, "manual start allows bounded attempt despite expired monitor");
        require(sim.ctx().evidenceSequence() == 0 && sim.ctx().monitor().inactiveMs() == 400,
            "manual start never erases inactivity or manufactures proof");
        root.stop(ExitReason.STOPPED);
    }

    private void recoveryProgress() {
        Simulation success = new Simulation(new ProgressPolicy(Set.of(EvidenceKind.MILESTONE), 100));
        boolean[] completed = { false };
        TreeHooks successHooks = new TreeHooks(message -> { });
        successHooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(leased(
            new SimulatedAction("task-success", ctx -> completed[0] ? Success.INSTANCE : Running.WORKING)))));
        successHooks.applyPendingChanges(success.ctx());
        Recovery successfulRecovery = new Recovery(new SimulatedAction("confirmed-recovery", ctx -> {
            ctx.progress("confirmed recovery milestone");
            return Success.INSTANCE;
        }), new Succeed(), message -> { });
        TreeRoot successRoot = new TreeRoot(success.ctx(), successHooks, successfulRecovery);
        successRoot.start();
        for (int i = 0; i < 3; i++) success.tick(successRoot, SimulatedSensors.idle());
        require(successRoot.recoveryLevel() == 1, "recovery level retained before task success");
        for (int i = 0; i < 2; i++) success.tick(successRoot, SimulatedSensors.idle());
        require(successRoot.recoveryLevel() == 2 && success.ctx().evidenceSequence() == 1,
            "confirmed recovery progress permits task to resume without clearing level");
        completed[0] = true;
        success.tick(successRoot, SimulatedSensors.idle());
        require(successRoot.recoveryLevel() == 0 && success.ctx().evidenceSequence() == 1, "task success clears recovery level without fake evidence");
        successRoot.stop(ExitReason.STOPPED);

        Simulation progress = new Simulation(new ProgressPolicy(Set.of(EvidenceKind.MILESTONE), 100));
        boolean[] prove = { false };
        SimulatedAction task = new SimulatedAction("sustained", ctx -> {
            if (prove[0]) ctx.progress("confirmed simulated milestone");
            return Running.WORKING;
        });
        task.limit(3_000);
        TreeHooks progressHooks = new TreeHooks(message -> { });
        progressHooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(leased(new SimulatedAction("initial-task")))));
        progressHooks.applyPendingChanges(progress.ctx());
        Recovery sustainedRecovery = new Recovery(task, new Succeed(), message -> { });
        TreeRoot progressRoot = new TreeRoot(progress.ctx(), progressHooks, sustainedRecovery);
        progressRoot.start();
        for (int i = 0; i < 5; i++) progress.tick(progressRoot, SimulatedSensors.idle());
        prove[0] = true;
        for (int i = 0; i < 1_200; i++) progress.tick(progressRoot, SimulatedSensors.idle());
        require(progressRoot.recoveryLevel() == 2, "recovery level waits 60 seconds of accepted progress");
        progress.tick(progressRoot, SimulatedSensors.idle());
        require(progressRoot.recoveryLevel() == 0, "60 seconds of sustained accepted progress clears recovery level");
        progressRoot.stop(ExitReason.STOPPED);
    }
}
