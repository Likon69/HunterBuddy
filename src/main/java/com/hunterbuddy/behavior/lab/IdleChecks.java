package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.BehaviorEngine;
import com.hunterbuddy.behavior.core.Clock;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.RestartBudget;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.TreeRoot;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.SafeRestart;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.routine.DeathRoutine;
import com.hunterbuddy.behavior.routine.IdleRoutine;
import com.hunterbuddy.behavior.routine.RoutineContext;
import com.hunterbuddy.behavior.routine.SessionRoutine;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class IdleChecks {
    private final List<String> checks = new ArrayList<>();

    private IdleChecks() {
    }

    public static List<String> run() {
        IdleChecks check = new IdleChecks();
        check.priority();
        check.bounds();
        check.resumeTask();
        check.sharedBudget();
        check.sessionAndDeath();
        check.fault();
        check.engine();
        return List.copyOf(check.checks);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("idle check failed: " + name);
        checks.add(name);
    }

    private TreeRoot root(Simulation sim, IdleRoutine idle, RestartBudget restarts, Condition ready, SimulatedAction action) {
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalArgumentException(message); });
        RoutineContext context = new RoutineContext(sim.ctx());
        idle.entries(context).forEach(hooks::insert);
        idle.onInstall(context);
        new SessionRoutine().entries(context).forEach(hooks::insert);
        new DeathRoutine().entries(context).forEach(hooks::insert);
        Node task = new Timeout(1_000, new WithLease(Set.of(Resource.MOVEMENT), LeasePolicy.WAIT, action));
        hooks.insert(new HookEntry(Hook.BOT, "bot:lab", Contributor.BOT, 0, new Guard(ready, task), true, task));
        hooks.applyPendingChanges(sim.ctx());
        return new TreeRoot(sim.ctx(), hooks, new Recovery(new Succeed(), new Succeed(), message -> { }, restarts));
    }

    private Condition unavailable() {
        return new Condition("unavailable", sensors -> false);
    }

    private void tick(Simulation sim, TreeRoot root, int count) {
        for (int i = 0; i < count; i++) sim.tick(root, SimulatedSensors.idle());
    }

    private void cycle(Simulation sim, Node node) {
        for (int i = 0; i < 601; i++) sim.tick(node);
    }

    private void priority() {
        Simulation sim = new Simulation();
        RestartBudget restarts = new RestartBudget();
        List<String> alerts = new ArrayList<>();
        IdleRoutine idle = new IdleRoutine(alerts::add, restarts);
        SimulatedAction action = new SimulatedAction("idle-priority");
        boolean[] ready = {true};
        TreeRoot root = root(sim, idle, restarts, new Condition("ready", sensors -> ready[0]), action);
        HookEntry entry = idle.entries(new RoutineContext(sim.ctx())).getFirst();
        require(entry.hook() == Hook.IDLE && entry.contributor() == Contributor.CORE && entry.flightAware()
            && entry.owner().equals("core:idle") && entry.node() instanceof SafeRestart,
            "IdleRoutine is a flight-aware core fallback entry in IDLE");
        root.start();
        tick(sim, root, 1);
        require(action.ticks() == 1 && idle.status().state().equals("ready") && alerts.isEmpty(),
            "IdleRoutine does not run while a higher-priority task is eligible");
        ready[0] = false;
        tick(sim, root, 1);
        require(idle.status().state().equals("active") && action.cleanups() == 1
            && sim.ctx().leases().snapshot().isEmpty() && alerts.equals(List.of("SafeIdle: resources released")),
            "IdleRoutine releases resources and alerts after the task loses eligibility");
        tick(sim, root, 10);
        require(alerts.size() == 1 && restarts.count() == 0 && sim.ctx().evidenceSequence() == 0,
            "Idle wait neither floods alerts nor spends restart budget nor creates progress");
        ready[0] = true;
        tick(sim, root, 1);
        require(action.starts() == 2 && idle.status().state().equals("ready") && restarts.count() == 0,
            "A newly eligible higher-priority task preempts IdleRoutine immediately");
        root.stop(ExitReason.STOPPED, "idle check stopped");
        idle.onUninstall(root.stopReport().orElseThrow());
        require(idle.status().state().equals("stopped") && idle.status().detail().equals("idle check stopped")
            && sim.ctx().leases().snapshot().isEmpty(), "IdleRoutine exposes the final stop report without orphan resources");
        List<HookEntry> entries = idle.entries(new RoutineContext(sim.ctx()));
        idle.onInstall(new RoutineContext(sim.ctx()));
        require(idle.entries(new RoutineContext(sim.ctx())) == entries && idle.status().state().equals("ready"),
            "IdleRoutine reinstall retains its nodes and clears stopped status");
    }

    private void bounds() {
        Simulation sim = new Simulation();
        RestartBudget restarts = new RestartBudget();
        List<String> alerts = new ArrayList<>();
        IdleRoutine idle = new IdleRoutine(alerts::add, restarts);
        SimulatedAction action = new SimulatedAction("idle-unavailable");
        TreeRoot root = root(sim, idle, restarts, unavailable(), action);
        root.start();
        tick(sim, root, 600);
        require(restarts.count() == 0 && idle.status().state().equals("active") && action.starts() == 0,
            "IdleRoutine waits the full thirty seconds before restarting");
        tick(sim, root, 1);
        require(restarts.count() == 1 && root.state() == RootState.RUNNING && sim.ctx().evidenceSequence() == 0
            && sim.ctx().monitor().inactiveMs() > 0, "Idle restart preserves evidence state and accumulated inactivity");
        require(sim.ring().snapshot().stream().anyMatch(event -> event.kind() == Kind.BOT
            && event.hook() == Hook.IDLE && event.reason().equals("restart")), "Idle automatic restart appears in important event trace");
        tick(sim, root, 1_803);
        require(root.state() == RootState.STOPPED && restarts.count() == 3 && alerts.size() == 4
            && root.stopReport().orElseThrow().detail().equals("automatic restart limit reached"),
            "IdleRoutine permits three restarts per hour and stops on the fourth attempt with a precise cause");
        require(sim.ctx().leases().snapshot().isEmpty() && action.starts() == 0 && sim.ctx().evidenceSequence() == 0,
            "Idle restart exhaustion leaves no action or lease and never manufactures progress");
        root.start();
        tick(sim, root, 601);
        require(root.state() == RootState.STOPPED && restarts.count() == 3,
            "Stopping and starting the root cannot refund the shared restart budget");
    }

    private void sharedBudget() {
        Simulation sim = new Simulation();
        RestartBudget restarts = new RestartBudget();
        List<String> stops = new ArrayList<>();
        sim.ctx().stopHandler(stops::add);
        IdleRoutine routine = new IdleRoutine(message -> { }, restarts);
        Node idle = routine.entries(new RoutineContext(sim.ctx())).getFirst().guard();
        TreeValidator.bind(idle, "idle", "core:idle", Hook.IDLE);
        Recovery recovery = new Recovery(new Succeed(), new Succeed(), message -> { }, restarts);
        TreeValidator.validate(idle, recovery);
        TreeValidator.bind(recovery, "recovery", "core", Hook.RECOVERY);
        cycle(sim, idle);
        for (int i = 0; i < 4; i++) recovery.next(sim.ctx());
        cycle(sim, recovery);
        require(restarts.count() == 2 && recovery.restartCount() == 2 && recovery.restartReady(),
            "Idle and recovery level four spend the same rolling restart budget");
        cycle(sim, idle);
        recovery.reset();
        for (int i = 0; i < 4; i++) recovery.next(sim.ctx());
        cycle(sim, recovery);
        require(restarts.count() == 3 && !recovery.restartReady()
            && stops.equals(List.of("automatic restart limit reached")),
            "Alternating IDLE and RECOVERY cannot bypass the three-restart limit");
        sim.advance(3_600_000);
        cycle(sim, idle);
        require(restarts.count() == 1 && stops.size() == 1 && sim.ctx().evidenceSequence() == 0,
            "Shared restart entries expire after one hour without pretending that restart is progress");
    }

    private void resumeTask() {
        Simulation sim = new Simulation();
        RestartBudget restarts = new RestartBudget();
        IdleRoutine idle = new IdleRoutine(message -> { }, restarts);
        boolean[] ready = {false};
        SimulatedAction action = new SimulatedAction("idle-resumed-task");
        TreeRoot root = root(sim, idle, restarts, new Condition("ready after restart", sensors -> ready[0]), action);
        root.start();
        tick(sim, root, 601);
        ready[0] = true;
        tick(sim, root, 1);
        require(restarts.count() == 1 && action.starts() == 1 && action.ticks() == 1
            && idle.status().state().equals("ready") && root.state() == RootState.RUNNING,
            "Idle automatic restart rechecks the task on the next tick without reinstalling the bot");
        root.stop(ExitReason.STOPPED);
    }

    private void sessionAndDeath() {
        Simulation sim = new Simulation();
        RestartBudget restarts = new RestartBudget();
        IdleRoutine idle = new IdleRoutine(message -> { }, restarts);
        TreeRoot root = root(sim, idle, restarts, unavailable(), new SimulatedAction("idle-session"));
        root.start();
        tick(sim, root, 500);
        sim.tick(root, SimulatedSensors.session(true, false, false, false));
        long inactive = sim.ctx().monitor().inactiveMs();
        for (int i = 0; i < 20; i++) sim.tick(root, SimulatedSensors.session(true, false, false, false));
        require(root.state() == RootState.PAUSED && idle.status().state().equals("ready")
            && restarts.count() == 0 && sim.ctx().monitor().inactiveMs() == inactive,
            "SESSION preempts IdleRoutine and freezes inactivity without spending a restart");
        tick(sim, root, 101);
        require(root.state() == RootState.RUNNING && restarts.count() == 1,
            "SESSION pause does not refund the time already spent in IdleRoutine");
        sim.tick(root, SimulatedSensors.session(false, false, false, true));
        require(root.state() == RootState.STOPPED && root.stopReport().orElseThrow().reason() == ExitReason.DEAD
            && restarts.count() == 1 && sim.ctx().leases().snapshot().isEmpty(),
            "DEATH outranks IdleRoutine and stops immediately without another restart");
    }

    private void fault() {
        Simulation sim = new Simulation();
        RestartBudget restarts = new RestartBudget();
        IdleRoutine idle = new IdleRoutine(message -> { throw new IllegalStateException("idle alert fault"); }, restarts);
        TreeRoot root = root(sim, idle, restarts, unavailable(), new SimulatedAction("idle-fault"));
        sim.ctx().leases().request("unused", Set.of(Resource.MOVEMENT));
        root.start();
        tick(sim, root, 1);
        require(root.state() == RootState.STOPPED && root.stopReport().orElseThrow().reason() == ExitReason.FAULT
            && root.stopReport().orElseThrow().detail().contains("idle alert fault")
            && sim.ctx().leases().snapshot().isEmpty() && restarts.count() == 0,
            "Idle alert exception produces a clean FAULT stop without leaking a resource or restart");
    }

    private void engine() {
        long[] time = {0};
        List<String> alerts = new ArrayList<>();
        BehaviorEngine engine = new BehaviorEngine(new Clock(() -> time[0]), report -> { }, alerts::add);
        require(engine.routines().stream().anyMatch(routine -> routine instanceof IdleRoutine),
            "BehaviorEngine installs IdleRoutine alongside SESSION and DEATH");
        engine.startBot(new LabBot(LabScenario.WORK));
        time[0] += 50;
        engine.tick();
        require(engine.snapshot().hook().equals("BOT") && alerts.isEmpty()
            && engine.snapshot().routines().stream().anyMatch(status -> status.name().equals("idle") && status.state().equals("ready")),
            "Engine HUD snapshot exposes ready IdleRoutine while LabBot works");
        engine.stopBot(ExitReason.STOPPED, "idle engine stopped");
        require(engine.snapshot().routines().stream().allMatch(status -> status.state().equals("stopped"))
            && engine.snapshot().leases() == 0, "Engine stop cleans and uninstalls IdleRoutine through the Routine contract");
    }
}
