package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.BehaviorEngine;
import com.hunterbuddy.behavior.core.BehaviorStatus;
import com.hunterbuddy.behavior.core.Clock;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.ProgressPolicy;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.RootState;
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
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.routine.DeathRoutine;
import com.hunterbuddy.behavior.routine.Routine;
import com.hunterbuddy.behavior.routine.RoutineContext;
import com.hunterbuddy.behavior.routine.SessionRoutine;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SessionChecks {
    private final List<String> checks = new ArrayList<>();

    private SessionChecks() {
    }

    public static List<String> run() {
        SessionChecks check = new SessionChecks();
        check.priority();
        check.limits();
        check.recoveryPause();
        check.engineLifecycle();
        return List.copyOf(check.checks);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("session check failed: " + name);
        checks.add(name);
    }

    private Simulation simulation() {
        return new Simulation(new ProgressPolicy(Set.of(EvidenceKind.MILESTONE), 100));
    }

    private Recovery recovery() {
        return new Recovery(new Fail(FailureCode.NO_OPTION, "none"), new Succeed(), message -> { });
    }

    private TreeRoot root(Simulation sim, SimulatedAction action, Recovery recovery, List<Routine> routines) {
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalArgumentException(message); });
        RoutineContext context = new RoutineContext(sim.ctx());
        for (Routine routine : routines) {
            routine.entries(context).forEach(hooks::insert);
            routine.onInstall(context);
        }
        Node task = new Timeout(50_000, new WithLease(Set.of(Resource.LOOK), LeasePolicy.WAIT, action));
        hooks.insert(new HookEntry(Hook.BOT, "bot:lab", Contributor.BOT, 0,
            new Guard(new Condition("work", sensors -> true), task), true, task));
        hooks.applyPendingChanges(sim.ctx());
        return new TreeRoot(sim.ctx(), hooks, recovery);
    }

    private void priority() {
        Simulation sim = simulation();
        SimulatedAction action = new SimulatedAction("session-priority");
        Routine session = new SessionRoutine();
        List<Routine> routines = List.of(session, new DeathRoutine());
        TreeRoot root = root(sim, action, recovery(), routines);
        root.start();
        sim.tick(root, SimulatedSensors.session(false, true, true, false));
        require(action.ticks() == 1 && root.state() == RootState.RUNNING, "bot-owned screen never activates SESSION pause");
        sim.tick(root, SimulatedSensors.session(true, true, false, false));
        require(root.state() == RootState.PAUSED && root.stopReport().isEmpty() && root.recoveryLevel() == 0
            && session.status().state().equals("active") && session.status().detail().equals("session-queue"),
            "live-player queue SESSION outranks foreign screen and no-progress monitor");
        require(action.ticks() == 1 && action.cleanups() == 1 && sim.ctx().leases().snapshot().isEmpty(),
            "SESSION preempts bot before another work tick and releases its lease");
        require(sim.ctx().monitor().inactiveMs() == 0 && sim.ctx().clock().activeMs() == 0 && sim.ctx().evidenceSequence() == 0,
            "known SESSION pause consumes no active time and fabricates no progress");
        sim.tick(root, SimulatedSensors.session(false, true, false, false));
        require(root.state() == RootState.PAUSED && session.status().detail().equals("session-screen") && root.stopReport().isEmpty(),
            "live-player foreign screen SESSION remains paused when queue ends");
        sim.tick(root, SimulatedSensors.session(false, false, false, true));
        require(root.state() == RootState.STOPPED && root.stopReport().orElseThrow().reason() == ExitReason.DEAD && action.ticks() == 1,
            "DEATH executes immediately after the higher SESSION guard clears");
        for (Routine routine : routines) routine.onUninstall(root.stopReport().orElseThrow());
        require(routines.stream().allMatch(routine -> routine.status().state().equals("stopped")),
            "routine uninstall records clean stop status through shared contract");
        RoutineContext context = new RoutineContext(sim.ctx());
        List<HookEntry> retained = session.entries(context);
        session.onInstall(context);
        require(session.entries(context) == retained && session.status().state().equals("ready"),
            "routine reinstall retains its entries and clears prior stop status");
        try {
            session.entries(new RoutineContext(new Simulation().ctx()));
            throw new IllegalStateException("routine moved to another context");
        } catch (IllegalStateException ex) {
            require(ex.getMessage().contains("another context"), "routine cannot share node instances across different engines");
        }
    }

    private void limits() {
        Simulation sim = simulation();
        SimulatedAction action = new SimulatedAction("screen-limit");
        TreeRoot root = root(sim, action, recovery(), List.of(new SessionRoutine(), new DeathRoutine()));
        root.start();
        for (int i = 0; i < 1_199; i++) sim.tick(root, SimulatedSensors.session(false, true, false, false));
        require(root.state() == RootState.PAUSED && action.starts() == 0 && root.stopReport().isEmpty(),
            "foreign screen pause lasts through its 1199th waiting tick");
        sim.tick(root, SimulatedSensors.session(false, true, false, false));
        require(root.state() == RootState.STOPPED
            && root.stopReport().orElseThrow().detail().contains("'simulated screen'")
            && root.stopReport().orElseThrow().detail().contains("60-second limit")
            && sim.ctx().leases().snapshot().isEmpty() && action.starts() == 0,
            "foreign screen timeout stops with precise cause and never falls through to BOT");
        require(sim.ctx().monitor().inactiveMs() == 0 && root.recoveryLevel() == 0,
            "foreign screen timeout uses its own bound without spurious recovery");
        sim = simulation();
        action = new SimulatedAction("named-screen-limit");
        root = root(sim, action, recovery(), List.of(new SessionRoutine(), new DeathRoutine()));
        root.start();
        for (int i = 0; i < 1_200; i++) {
            sim.advance(100);
            root.tick(SimulatedSensors.session(false, true, false, false, "AnvilScreen"));
        }
        require(root.stopReport().orElseThrow().detail().equals(
            "Player-owned screen 'AnvilScreen': waiting for 119.9 seconds (60-second limit)"),
            "screen timeout names actual screen and measures elapsed wait instead of assuming twenty ticks per second");
        sim = simulation();
        action = new SimulatedAction("queue-limit");
        root = root(sim, action, recovery(), List.of(new SessionRoutine(), new DeathRoutine()));
        root.start();
        for (int i = 0; i < 17_999; i++) sim.tick(root, SimulatedSensors.session(true, false, false, false));
        require(root.state() == RootState.PAUSED && root.stopReport().isEmpty(), "queue pause remains bounded at fifteen minutes");
        sim.tick(root, SimulatedSensors.session(true, false, false, false));
        require(root.state() == RootState.STOPPED && root.stopReport().orElseThrow().detail().equals("Session queue wait exceeded 15 minutes")
            && action.starts() == 0 && sim.ctx().monitor().inactiveMs() == 0,
            "queue timeout stops with precise cause without mining or fabricated inactivity");
    }

    private void recoveryPause() {
        Simulation sim = simulation();
        SimulatedAction action = new SimulatedAction("session-recovery");
        Recovery recovery = recovery();
        TreeRoot root = root(sim, action, recovery, List.of(new SessionRoutine(), new DeathRoutine()));
        root.start();
        for (int i = 0; i < 20 && !(recovery.active() && recovery.level() == 4); i++) sim.tick(root, SimulatedSensors.idle());
        require(recovery.active() && recovery.level() == 4, "SESSION recovery fixture reaches running SafeIdle");
        for (int i = 0; i < 500; i++) sim.tick(root, SimulatedSensors.idle());
        sim.tick(root, SimulatedSensors.session(true, false, false, false));
        long inactive = sim.ctx().monitor().inactiveMs();
        int actionTicks = action.ticks();
        require(root.state() == RootState.PAUSED && recovery.level() == 4 && !recovery.active()
            && recovery.children().stream().noneMatch(Node::active), "SESSION preempts every recovery descendant and preserves recovery level");
        for (int i = 0; i < 20; i++) sim.tick(root, SimulatedSensors.session(true, false, false, false));
        require(sim.ctx().monitor().inactiveMs() == inactive && action.ticks() == actionTicks && sim.ctx().evidenceSequence() == 0,
            "SESSION during recovery freezes inactivity without resuming work or clearing old evidence");
        require(sim.ring().snapshot().stream().noneMatch(event -> event.tick() == sim.ctx().tick()
            && (event.kind() == Kind.MONITOR || event.kind() == Kind.RECOVERY)),
            "SESSION retains priority over already-fired monitor and pending recovery");
        sim.tick(root, SimulatedSensors.idle());
        require(root.state() == RootState.RUNNING && recovery.active() && recovery.level() == 4,
            "clearing SESSION resumes the interrupted recovery level");
        for (int i = 0; i < 100 && recovery.restartCount() == 0; i++) sim.tick(root, SimulatedSensors.idle());
        require(recovery.restartCount() == 1 && sim.ctx().evidenceSequence() == 0,
            "SESSION pause does not refund the twenty-five seconds already spent in SafeIdle");
        root.stop(ExitReason.STOPPED);
        sim.ctx().end();
    }

    private void engineLifecycle() {
        long[] time = {0};
        BehaviorEngine engine = new BehaviorEngine(new Clock(() -> time[0]), report -> { }, message -> { });
        engine.startBot(new LabBot(LabScenario.WORK));
        time[0] += 50;
        engine.tick(SimulatedSensors.session(true, false, false, false));
        require(engine.snapshot().state() == RootState.PAUSED && engine.snapshot().hook().equals("SESSION")
            && engine.snapshot().routines().stream().anyMatch(status -> status.name().equals("session") && status.state().equals("active")),
            "engine exposes SESSION pause and active routine status to HUD snapshot");
        require(BehaviorStatus.lines(engine.snapshot()).stream().anyMatch(line -> line.contains("Routine session: active")),
            "command and HUD share readable routine status");
        try {
            engine.startBot(new LabBot(LabScenario.WORK));
            throw new IllegalStateException("start while paused accepted");
        } catch (IllegalStateException ex) {
            require(ex.getMessage().contains("already running"), "paused engine refuses duplicate start");
        }
        engine.switchBot(new LabBot(LabScenario.STALLED));
        time[0] += 50;
        engine.tick(SimulatedSensors.session(true, false, false, false));
        require(engine.snapshot().status().task().equals("STALLED") && engine.snapshot().leases() == 0,
            "paused engine applies requested bot switch without orphan lease");
        time[0] += 50;
        engine.tick(SimulatedSensors.session(true, false, false, false));
        require(engine.snapshot().state() == RootState.PAUSED && engine.snapshot().hook().equals("SESSION"),
            "SESSION routine survives bot replacement and pauses replacement before work");
        engine.stopBot(ExitReason.DISCONNECTED, "session check disconnected");
        require(engine.snapshot().state() == RootState.STOPPED && engine.snapshot().leases() == 0
            && engine.snapshot().routines().stream().allMatch(status -> status.state().equals("stopped")),
            "stop during SESSION pause cleans engine and reports stopped routine lifecycle");
        engine.startBot(new LabBot(LabScenario.FAULT));
        time[0] += 50;
        engine.tick();
        require(engine.snapshot().stop().reason() == ExitReason.FAULT
            && engine.snapshot().routines().stream().allMatch(status -> status.detail().equals(engine.snapshot().stop().detail())),
            "routine lifecycle receives final fault report with exact exception detail");
    }
}
