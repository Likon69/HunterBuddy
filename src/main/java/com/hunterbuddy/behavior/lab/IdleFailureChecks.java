package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.ProgressPolicy;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.RestartBudget;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.TreeRoot;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.ReactiveSelector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.UtilitySelector;
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

public final class IdleFailureChecks {
    private final List<String> checks = new ArrayList<>();

    private IdleFailureChecks() {
    }

    public static List<String> run() {
        IdleFailureChecks check = new IdleFailureChecks();
        check.immediateFailure();
        check.noUtilityOption();
        check.leaseRefusal();
        check.pauseAndRebuild();
        check.newAvailability();
        check.recovery();
        check.genericSelector();
        check.partialSuccess();
        return List.copyOf(check.checks);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("idle failure check failed: " + name);
        checks.add(name);
    }

    private Condition ready() {
        return new Condition("ready", sensors -> true);
    }

    private TreeHooks hooks() {
        return new TreeHooks(message -> { throw new IllegalArgumentException(message); });
    }

    private TreeRoot root(Simulation sim, TreeHooks hooks, RestartBudget restarts, List<String> alerts, Condition condition, Node task) {
        RoutineContext context = new RoutineContext(sim.ctx());
        new IdleRoutine(alerts::add, restarts).entries(context).forEach(hooks::insert);
        new SessionRoutine().entries(context).forEach(hooks::insert);
        new DeathRoutine().entries(context).forEach(hooks::insert);
        Node bounded = new Timeout(1_000, task);
        hooks.insert(new HookEntry(Hook.BOT, "bot:lab", Contributor.BOT, 0, new Guard(condition, bounded), true, bounded));
        hooks.applyPendingChanges(sim.ctx());
        return new TreeRoot(sim.ctx(), hooks, new Recovery(new Succeed(), new Succeed(), alerts::add, restarts));
    }

    private void tick(Simulation sim, TreeRoot root, int count) {
        for (int i = 0; i < count; i++) sim.tick(root, SimulatedSensors.idle());
    }

    private SimulatedAction failing(String name) {
        return new SimulatedAction(name, ctx -> new Failure(FailureCode.NO_OPTION, "nothing to do"));
    }

    private void immediateFailure() {
        Simulation sim = new Simulation();
        RestartBudget restarts = new RestartBudget();
        List<String> alerts = new ArrayList<>();
        SimulatedAction task = failing("immediate-failure");
        TreeRoot root = root(sim, hooks(), restarts, alerts, ready(), task);
        root.start();
        tick(sim, root, 100);
        require(alerts.size() == 1 && task.starts() == 5 && task.cleanups() == 5 && restarts.count() == 0,
            "One hundred immediate-failure ticks produce one wait alert and five spaced BOT attempts");
        tick(sim, root, 2_304);
        require(root.state() == RootState.STOPPED && restarts.count() == 3 && task.starts() == 5 && alerts.size() == 1
            && root.stopReport().orElseThrow().detail().equals("automatic restart limit reached"),
            "Persistent BOT failure preserves the Action start budget and gets three restarts then one precise stop without repeated wait alerts");
        require(sim.ctx().evidenceSequence() == 0 && sim.ctx().leases().snapshot().isEmpty(),
            "Deferring failed BOT retries neither invents progress nor leaves resources behind");
    }

    private void noUtilityOption() {
        Simulation sim = new Simulation();
        List<String> alerts = new ArrayList<>();
        TreeRoot root = root(sim, hooks(), new RestartBudget(), alerts, ready(), new UtilitySelector("empty-tasks"));
        root.start();
        tick(sim, root, 100);
        long starts = sim.ring().snapshot().stream().filter(event -> event.kind() == Kind.START
            && event.nodePath().endsWith(":empty-tasks")).count();
        require(starts == 5 && alerts.size() == 1 && root.state() == RootState.RUNNING,
            "UtilitySelector NO_OPTION polls once per second within a single signaled idle period");
        root.stop(ExitReason.STOPPED);
    }

    private void leaseRefusal() {
        Simulation sim = new Simulation();
        List<String> alerts = new ArrayList<>();
        RestartBudget restarts = new RestartBudget();
        SimulatedAction work = new SimulatedAction("lease-refused-work");
        TreeRoot root = root(sim, hooks(), restarts, alerts, ready(),
            new WithLease(Set.of(Resource.MOVEMENT), LeasePolicy.FAIL_FAST, work));
        sim.ctx().leases().request("other", Set.of(Resource.MOVEMENT));
        root.start();
        tick(sim, root, 20);
        require(work.starts() == 0 && alerts.size() == 1 && restarts.count() == 0 && sim.ctx().leases().snapshot().isEmpty(),
            "FAIL_FAST refusal waits without oscillating and SafeIdle releases the blocking lease");
        tick(sim, root, 1);
        require(work.starts() == 1 && work.ticks() == 1 && restarts.count() == 0 && alerts.size() == 1
            && sim.ctx().leases().holder(Resource.MOVEMENT).orElseThrow().owner().equals("bot:lab"),
            "One-second retry after lease refusal reacquires movement without consuming an automatic restart");
        root.stop(ExitReason.STOPPED);
    }

    private void pauseAndRebuild() {
        Simulation sim = new Simulation();
        TreeHooks hooks = hooks();
        List<String> alerts = new ArrayList<>();
        SimulatedAction task = failing("paused-failure");
        RestartBudget restarts = new RestartBudget();
        TreeRoot root = root(sim, hooks, restarts, alerts, ready(), task);
        root.start();
        tick(sim, root, 100);
        Node plugin = new Succeed();
        hooks.insert(new HookEntry(Hook.BOT_PRE, "plugin:inactive", Contributor.PLUGIN, 0,
            new Guard(new Condition("inactive", sensors -> false), plugin), true, plugin));
        tick(sim, root, 50);
        require(task.starts() == 5 && alerts.size() == 1, "Hook rebuild retains bounded BOT attempts and the idle notification");
        for (int i = 0; i < 20; i++) sim.tick(root, SimulatedSensors.session(true, false, false, false));
        require(root.state() == RootState.PAUSED && restarts.count() == 0, "SESSION remains immediately eligible during a deferred BOT retry");
        tick(sim, root, 50);
        require(task.starts() == 5 && alerts.size() == 1 && root.state() == RootState.RUNNING,
            "SESSION resume retains the inactivity notification and does not refund the Action start budget");
        sim.tick(root, SimulatedSensors.session(false, false, false, true));
        require(root.state() == RootState.STOPPED && root.stopReport().orElseThrow().reason() == ExitReason.DEAD,
            "DEATH still stops immediately during the protected idle wait");
        root.start();
        tick(sim, root, 1);
        require(task.starts() == 5 && alerts.size() == 2 && sim.ring().snapshot().stream().anyMatch(event -> event.tick() == sim.ctx().tick()
            && event.hook() == Hook.BOT && event.kind() == Kind.START),
            "Explicit stop and start clear retry deferral and signal a new idle period without refunding the Action budget");
        root.stop(ExitReason.STOPPED);
    }

    private void newAvailability() {
        Simulation sim = new Simulation();
        List<String> alerts = new ArrayList<>();
        boolean[] ready = {true};
        boolean[] working = {false};
        SimulatedAction task = new SimulatedAction("new-availability", ctx -> working[0]
            ? Running.WORKING : new Failure(FailureCode.NO_OPTION, "nothing to do"));
        TreeRoot root = root(sim, hooks(), new RestartBudget(), alerts, new Condition("ready", sensors -> ready[0]), task);
        root.start();
        tick(sim, root, 5);
        ready[0] = false;
        tick(sim, root, 1);
        ready[0] = true;
        working[0] = true;
        tick(sim, root, 1);
        require(task.starts() == 2 && task.active() && alerts.size() == 1,
            "A newly eligible BOT can resume immediately after its guard was observed false");
        working[0] = false;
        tick(sim, root, 10);
        require(task.starts() == 2 && alerts.size() == 2,
            "Work resumption closes the old idle period so a subsequent genuine wait is signaled once");
        root.stop(ExitReason.STOPPED);
    }

    private void recovery() {
        Simulation sim = new Simulation(new ProgressPolicy(Set.of(EvidenceKind.MILESTONE), 100));
        List<String> alerts = new ArrayList<>();
        RestartBudget restarts = new RestartBudget();
        TreeRoot root = root(sim, hooks(), restarts, alerts, ready(), failing("recovery-failure"));
        root.start();
        for (int i = 0; i < 3_000 && root.state() != RootState.STOPPED; i++) tick(sim, root, 1);
        require(root.state() == RootState.STOPPED && restarts.count() == 3 && alerts.size() == 1,
            "Crossing from IDLE into RECOVERY preserves one inactivity alert and the shared three-restart bound");
    }

    private void genericSelector() {
        Simulation sim = new Simulation();
        SimulatedAction failed = failing("generic-failure");
        SimulatedAction fallback = new SimulatedAction("generic-fallback");
        Node selector = new ReactiveSelector("generic", new Guard(ready(), new Timeout(100, failed)),
            new Guard(ready(), new Timeout(100, fallback)));
        TreeValidator.validate(selector);
        TreeValidator.bind(selector, "generic", "bot:lab", Hook.BOT);
        for (int i = 0; i < 3; i++) sim.tick(selector);
        require(failed.starts() == 3 && fallback.starts() == 3,
            "ReactiveSelector without an IDLE hook retains its normal reactive fallback behavior");
        selector.halt(sim.ctx(), ExitReason.STOPPED);
    }

    private void partialSuccess() {
        Simulation sim = new Simulation();
        RestartBudget restarts = new RestartBudget();
        List<String> alerts = new ArrayList<>();
        Node task = new Sequence("partial-task", new ReactiveSelector("internal", new Guard(ready(), new Succeed())),
            new Fail(FailureCode.NO_OPTION, "still unavailable"));
        TreeRoot root = root(sim, hooks(), restarts, alerts, ready(), task);
        root.start();
        tick(sim, root, 2_404);
        require(root.state() == RootState.STOPPED && restarts.count() == 3 && alerts.size() == 1,
            "Success inside an ultimately failing BOT does not end the inactivity period or duplicate its alert");
    }
}
