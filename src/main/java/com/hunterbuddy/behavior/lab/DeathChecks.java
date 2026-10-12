package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.BehaviorEngine;
import com.hunterbuddy.behavior.core.Clock;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.ProgressPolicy;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.core.TreeRoot;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.mc.BehaviorMessages;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.routine.DeathRoutine;
import com.hunterbuddy.behavior.routine.RoutineContext;
import com.hunterbuddy.behavior.routine.SessionRoutine;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class DeathChecks {
    private final List<String> checks = new ArrayList<>();

    private DeathChecks() {
    }

    public static List<String> run() {
        DeathChecks check = new DeathChecks();
        check.preemption();
        check.monitorPriority();
        check.recoveryPriority();
        check.screenDeath();
        check.engineLifecycle();
        return List.copyOf(check.checks);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("death check failed: " + name);
        checks.add(name);
    }

    private Recovery recovery() {
        return new Recovery(new Fail(FailureCode.NO_OPTION, "none"), new Succeed(), message -> { });
    }

    private TreeRoot root(Simulation sim, SimulatedAction action, Recovery recovery) {
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalArgumentException(message); });
        Node task = new Timeout(2_000, new WithLease(Set.of(Resource.LOOK), LeasePolicy.WAIT, action));
        hooks.insert(new HookEntry(Hook.BOT, "bot:lab", Contributor.BOT, 0,
            new Guard(new Condition("work", sensors -> true), task), true, task));
        new DeathRoutine().entries(new RoutineContext(sim.ctx())).forEach(hooks::insert);
        new SessionRoutine().entries(new RoutineContext(sim.ctx())).forEach(hooks::insert);
        hooks.applyPendingChanges(sim.ctx());
        return new TreeRoot(sim.ctx(), hooks, recovery);
    }

    private void preemption() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("work");
        TreeRoot root = root(sim, action, recovery());
        root.start();
        sim.tick(root, SimulatedSensors.idle());
        require(root.state() == RootState.RUNNING && action.ticks() == 1 && sim.ctx().leases().snapshot().size() == 1,
            "death guard leaves live player task and lease active");
        sim.tick(root, SimulatedSensors.capture(true, false, false, true, 0, 0));
        StopReport report = root.stopReport().orElseThrow();
        require(root.state() == RootState.STOPPED && report.reason() == ExitReason.DEAD
            && report.detail().equals("Player died") && report.owner().equals("bot:lab"),
            "simulated death in flight preserves DEAD detail and bot owner");
        require(action.ticks() == 1 && action.cleanups() == 1 && !action.active() && sim.ctx().leases().snapshot().isEmpty(),
            "death preempts work before another action tick and cleans its lease once");
        require(sim.ring().snapshot().stream().anyMatch(event -> event.hook() == Hook.DEATH && event.kind() == Kind.BOT
            && event.reason().equals("stop") && event.detail().equals("Player died")),
            "death stop is traced from the core DEATH hook");
        require(sim.ctx().evidenceSequence() == 0, "death signal never manufactures progress");
        sim.tick(root, SimulatedSensors.idle());
        require(root.state() == RootState.STOPPED && root.stopReport().orElseThrow() == report && action.starts() == 1,
            "simulated respawn does not restart bot or replace original death report");
        root.start();
        sim.tick(root, SimulatedSensors.idle());
        require(action.starts() == 2 && root.stopReport().isEmpty(), "explicit restart after death opens a fresh task activation");
        root.stop(ExitReason.STOPPED);
        sim.ctx().end();
    }

    private void monitorPriority() {
        Simulation sim = new Simulation(new ProgressPolicy(Set.of(EvidenceKind.MILESTONE), 100));
        SimulatedAction action = new SimulatedAction("monitor-death");
        TreeRoot root = root(sim, action, recovery());
        root.start();
        sim.tick(root, SimulatedSensors.idle());
        sim.tick(root, SimulatedSensors.idle());
        sim.tick(root, SimulatedSensors.session(false, true, false, true, "DeathScreen"));
        require(root.stopReport().orElseThrow().reason() == ExitReason.DEAD && root.recoveryLevel() == 0,
            "death with open screen wins when no-progress deadline expires on the same tick");
        require(sim.ring().snapshot().stream().noneMatch(event -> event.tick() == sim.ctx().tick()
            && (event.kind() == Kind.MONITOR || event.kind() == Kind.RECOVERY)),
            "death tick starts no redundant recovery attempt");
    }

    private void recoveryPriority() {
        Simulation sim = new Simulation(new ProgressPolicy(Set.of(EvidenceKind.MILESTONE), 100));
        SimulatedAction action = new SimulatedAction("recovery-death");
        Recovery recovery = recovery();
        TreeRoot root = root(sim, action, recovery);
        root.start();
        for (int i = 0; i < 20 && !(recovery.active() && recovery.level() == 4); i++) sim.tick(root, SimulatedSensors.idle());
        require(recovery.active() && recovery.level() == 4, "death recovery fixture reaches active SafeIdle");
        int ticks = action.ticks();
        sim.tick(root, SimulatedSensors.session(false, true, false, true, "DeathScreen"));
        require(root.state() == RootState.STOPPED && root.stopReport().orElseThrow().reason() == ExitReason.DEAD
            && !recovery.active() && recovery.children().stream().noneMatch(Node::active),
            "DEATH with open screen interrupts SafeIdle and stops every recovery descendant");
        require(action.ticks() == ticks && sim.ctx().leases().snapshot().isEmpty() && sim.ctx().evidenceSequence() == 0,
            "death during recovery neither resumes work nor retains leases or creates proof");
    }

    private void screenDeath() {
        long[] time = {0};
        List<String> chat = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        List<StopReport> reports = new ArrayList<>();
        BehaviorMessages messages = new BehaviorMessages(chat::add, logs::add, () -> true);
        BehaviorEngine engine = new BehaviorEngine(new Clock(() -> time[0]), report -> {
            reports.add(report);
            messages.stopped(report);
        }, messages::alert);
        engine.startBot(new LabBot(LabScenario.WORK));
        time[0] += 50;
        engine.tick();
        time[0] += 50;
        engine.tick(SimulatedSensors.session(false, true, false, false, "InventoryScreen"));
        require(engine.snapshot().state() == RootState.PAUSED && reports.isEmpty(),
            "live foreign screen still pauses without a false death report");
        time[0] += 50;
        engine.tick(SimulatedSensors.session(false, true, false, true, "DeathScreen"));
        require(engine.snapshot().state() == RootState.STOPPED && reports.size() == 1
            && reports.getFirst().reason() == ExitReason.DEAD && reports.getFirst().nowMs() == time[0],
            "death screen interrupts existing SESSION pause and reports DEAD on the same tick");
        require(chat.equals(List.of("Stopped [DEAD]: Player died")) && engine.snapshot().leases() == 0,
            "death with foreign screen announces exactly once and leaves no lease");
        time[0] += 50;
        engine.tick();
        require(reports.size() == 1 && engine.snapshot().state() == RootState.STOPPED && engine.snapshot().status().completed() == 0,
            "fast respawn after death screen cannot resume task or erase death report");
        engine.startBot(new LabBot(LabScenario.WORK));
        time[0] += 50;
        engine.tick(SimulatedSensors.session(true, true, false, true, "DeathScreen"));
        require(reports.size() == 2 && reports.getLast().reason() == ExitReason.DEAD
            && engine.snapshot().state() == RootState.STOPPED && engine.snapshot().leases() == 0,
            "simultaneous queue and death screen cannot mask immediate DEAD stop");
    }

    private void engineLifecycle() {
        long[] time = {0};
        List<String> chat = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        List<StopReport> reports = new ArrayList<>();
        BehaviorMessages messages = new BehaviorMessages(chat::add, logs::add, () -> true);
        BehaviorEngine engine = new BehaviorEngine(new Clock(() -> time[0]), report -> {
            reports.add(report);
            messages.stopped(report);
        }, messages::alert);
        engine.startBot(new LabBot(LabScenario.WORK));
        time[0] += 50;
        engine.tick(SimulatedSensors.capture(true, false, false, false, 0, 0));
        require(engine.snapshot().state() == RootState.STOPPED && engine.snapshot().stop().reason() == ExitReason.DEAD
            && engine.snapshot().status().completed() == 0 && engine.snapshot().leases() == 0,
            "engine-installed death routine stops before first bot action");
        require(reports.size() == 1 && chat.equals(List.of("Stopped [DEAD]: Player died")) && logs.size() == 1,
            "engine death report announces one precise message in chat and log");
        engine.tick();
        engine.stopBot(ExitReason.STOPPED, "duplicate");
        require(reports.size() == 1 && engine.snapshot().stop().reason() == ExitReason.DEAD,
            "stopped engine does not announce death again or resume from live default sensors");
        engine.startBot(new LabBot(LabScenario.WORK));
        time[0] += 50;
        engine.tick();
        require(engine.snapshot().leases() == 1, "explicit engine restart retains functional core death guard and starts live work");
        engine.switchBot(new LabBot(LabScenario.FAULT));
        time[0] += 50;
        engine.tick(SimulatedSensors.capture(true, false, false, true, 0, 0));
        require(reports.size() == 2 && reports.getLast().reason() == ExitReason.DEAD && engine.snapshot().leases() == 0,
            "death cancels pending switch before replacement fault scenario can run");
        engine.startBot(new LabBot(LabScenario.WORK));
        engine.switchBot(new LabBot(LabScenario.STALLED));
        time[0] += 50;
        engine.tick();
        require(engine.snapshot().status().task().equals("STALLED"), "live bot switch succeeds while retaining core routine");
        time[0] += 50;
        engine.tick(SimulatedSensors.capture(true, false, false, false, 0, 0));
        require(reports.getLast().reason() == ExitReason.DEAD && engine.snapshot().leases() == 0
            && chat.getLast().equals("Stopped [DEAD]: Player died"),
            "core death hook remains installed after bot removal and replacement");
    }
}
