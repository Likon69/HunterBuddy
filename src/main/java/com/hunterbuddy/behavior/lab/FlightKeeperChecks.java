package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TreeRoot;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.Lease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.routine.DeathRoutine;
import com.hunterbuddy.behavior.routine.FlightKeeperRoutine;
import com.hunterbuddy.behavior.routine.RoutineContext;
import com.hunterbuddy.behavior.routine.SessionRoutine;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class FlightKeeperChecks {
    private final List<String> checks = new ArrayList<>();

    private FlightKeeperChecks() {
    }

    public static List<String> run() {
        FlightKeeperChecks check = new FlightKeeperChecks();
        check.guards();
        check.limit();
        check.priority();
        check.sessionAndDeath();
        check.lifecycle();
        return List.copyOf(check.checks);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("flight keeper check failed: " + name);
        checks.add(name);
    }

    private HookEntry prepare(Simulation sim, FlightKeeperRoutine routine) {
        RoutineContext context = new RoutineContext(sim.ctx());
        HookEntry entry = routine.entries(context).getFirst();
        routine.onInstall(context);
        TreeValidator.bind(entry.guard(), "flight-check", entry.owner(), entry.hook());
        return entry;
    }

    private Node leased(SimulatedAction action) {
        return new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT, action);
    }

    private void guards() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("flight-body");
        FlightKeeperRoutine routine = new FlightKeeperRoutine(leased(action));
        HookEntry entry = prepare(sim, routine);
        require(entry.hook() == Hook.FLIGHT && entry.contributor() == Contributor.CORE
            && entry.flightAware() && entry.owner().equals("core:flight-keeper"),
            "FlightKeeper is a flight-aware core entry in FLIGHT");
        require(sim.tick(entry.guard(), SimulatedSensors.flight(false, null)) instanceof Failure && action.starts() == 0,
            "FlightKeeper never starts its child on the ground");
        require(sim.tick(entry.guard(), SimulatedSensors.flight(true, "bot:pilot")) instanceof Failure && action.starts() == 0,
            "FlightKeeper never starts while another owner holds movement");
        require(sim.tick(entry.guard(), SimulatedSensors.flight(true, entry.owner())) instanceof Failure && action.starts() == 0,
            "Inactive FlightKeeper still requires unclaimed movement to enter");
        require(sim.tick(entry.guard(), SimulatedSensors.flight(true, null)) instanceof Running
            && action.starts() == 1 && routine.status().state().equals("active"),
            "FlightKeeper admits an unclaimed glide and reports active status");
        require(sim.ctx().leases().holder(Resource.MOVEMENT).orElseThrow().owner().equals(entry.owner())
            && sim.ctx().evidenceSequence() == 0, "FlightKeeper preserves child lease ownership without inventing progress");
        Lease movement = sim.ctx().leases().holder(Resource.MOVEMENT).orElseThrow();
        boolean retained = true;
        for (int i = 0; i < 3; i++) {
            String holder = sim.ctx().leases().holder(Resource.MOVEMENT).orElseThrow().owner();
            if (!(sim.tick(entry.guard(), SimulatedSensors.flight(true, holder)) instanceof Running)) {
                retained = false;
                break;
            }
        }
        require(retained && action.starts() == 1 && action.ticks() == 4 && action.cleanups() == 0
            && sim.ctx().leases().holder(Resource.MOVEMENT).orElse(null) == movement
            && sim.ctx().evidenceSequence() == 0,
            "FlightKeeper retains its activation and the same movement lease when sensors report its own holder");
        require(sim.tick(entry.guard(), SimulatedSensors.flight(true, "bot:pilot")) instanceof Failure failure
            && failure.code() == FailureCode.GUARD_LOST && action.stops().equals(List.of(ExitReason.GUARD_LOST))
            && sim.ctx().leases().snapshot().isEmpty(), "FlightKeeper guard loss halts descendants and releases leases");
        sim.tick(entry.guard(), SimulatedSensors.flight(true, null));
        String landedHolder = sim.ctx().leases().holder(Resource.MOVEMENT).orElseThrow().owner();
        require(sim.tick(entry.guard(), SimulatedSensors.flight(false, landedHolder)) instanceof Failure failure
            && failure.code() == FailureCode.GUARD_LOST && action.cleanups() == 2
            && sim.ctx().leases().snapshot().isEmpty(), "FlightKeeper landing removes eligibility and cleans the flight activation");

        SimulatedAction completed = new SimulatedAction("completed-flight", ctx -> Success.INSTANCE);
        HookEntry done = prepare(sim, new FlightKeeperRoutine(completed));
        require(sim.tick(done.guard(), SimulatedSensors.flight(true, null)) instanceof Success && completed.cleanups() == 1,
            "FlightKeeper transmits child success without adding another action");
    }

    private void limit() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("long-flight");
        action.limit(20_000);
        HookEntry entry = prepare(sim, new FlightKeeperRoutine(leased(action)));
        for (int i = 0; i < 11_999; i++) sim.tick(entry.guard(), SimulatedSensors.flight(true, null));
        require(action.ticks() == 11_999 && entry.guard().active(), "FlightKeeper allows work before its ten-minute active bound");
        entry.guard().halt(sim.ctx(), ExitReason.PAUSED);
        sim.advance(30_000);
        require(sim.tick(entry.guard(), SimulatedSensors.flight(true, null)) instanceof Running && action.ticks() == 12_000,
            "FlightKeeper pause freezes its remaining active timeout");
        require(sim.tick(entry.guard(), SimulatedSensors.flight(true, null)) instanceof Failure failure
            && failure.code() == FailureCode.TIMEOUT && action.ticks() == 12_000
            && action.stops().equals(List.of(ExitReason.PAUSED, ExitReason.ABORTED))
            && sim.ctx().leases().snapshot().isEmpty(), "FlightKeeper timeout cannot be refunded by pause and cleans descendants");
    }

    private HookEntry task(Hook hook, String owner, Contributor contributor, boolean flightAware, SimulatedAction action) {
        Node node = new Timeout(100, action);
        return new HookEntry(hook, owner, contributor, 0,
            new Guard(new Condition("ready", sensors -> true), node), flightAware, node);
    }

    private void priority() {
        Simulation sim = new Simulation();
        SimulatedAction flight = new SimulatedAction("priority-flight");
        FlightKeeperRoutine routine = new FlightKeeperRoutine(leased(flight));
        SimulatedAction combat = new SimulatedAction("priority-combat");
        SimulatedAction bot = new SimulatedAction("priority-bot");
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalArgumentException(message); });
        hooks.insert(task(Hook.BOT, "bot:lab", Contributor.BOT, true, bot));
        hooks.insert(task(Hook.COMBAT, "routine:combat", Contributor.ROUTINE, false, combat));
        routine.entries(new RoutineContext(sim.ctx())).forEach(hooks::insert);
        hooks.applyPendingChanges(sim.ctx());
        sim.tick(hooks.build(), SimulatedSensors.flight(true, null));
        require(flight.ticks() == 1 && combat.starts() == 0 && bot.starts() == 0,
            "FLIGHT outranks COMBAT and BOT regardless of insertion order");
        String holder = sim.ctx().leases().holder(Resource.MOVEMENT).orElseThrow().owner();
        sim.tick(hooks.build(), SimulatedSensors.flight(true, holder));
        require(flight.ticks() == 2 && flight.starts() == 1 && flight.cleanups() == 0
            && combat.starts() == 0 && bot.starts() == 0,
            "ReactiveSelector keeps FlightKeeper selected while its child holds movement");
        sim.tick(hooks.build(), SimulatedSensors.flight(true, "bot:pilot"));
        require(flight.cleanups() == 1 && combat.starts() == 0 && bot.ticks() == 1
            && sim.ctx().leases().snapshot().isEmpty(), "Non-flight-aware COMBAT remains excluded during a claimed glide");
        sim.tick(hooks.build(), SimulatedSensors.flight(false, null));
        require(combat.ticks() == 1 && bot.cleanups() == 1,
            "COMBAT regains eligibility after landing and preempts lower-priority BOT");
        hooks.haltAll(sim.ctx(), ExitReason.STOPPED);
        require(hooks.entries().stream().noneMatch(entry -> entry.guard().active())
            && sim.ctx().leases().snapshot().isEmpty(), "Stopping a flight hook tree leaves no running descendant or lease");
    }

    private void sessionAndDeath() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("protected-flight");
        FlightKeeperRoutine flight = new FlightKeeperRoutine(leased(action));
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalArgumentException(message); });
        RoutineContext context = new RoutineContext(sim.ctx());
        flight.entries(context).forEach(hooks::insert);
        new DeathRoutine().entries(context).forEach(hooks::insert);
        new SessionRoutine().entries(context).forEach(hooks::insert);
        hooks.applyPendingChanges(sim.ctx());
        TreeRoot root = new TreeRoot(sim.ctx(), hooks, new Recovery(new Succeed(), new Succeed(), message -> { }));
        root.start();
        sim.tick(root, SimulatedSensors.flight(true, null));
        String holder = sim.ctx().leases().holder(Resource.MOVEMENT).orElseThrow().owner();
        sim.tick(root, SimulatedSensors.flight(true, holder));
        require(root.state() == RootState.RUNNING && action.starts() == 1 && action.ticks() == 2
            && action.cleanups() == 0 && flight.status().state().equals("active"),
            "TreeRoot retains FlightKeeper when the next sensor snapshot reflects its own movement lease");
        sim.tick(root, SimulatedSensors.session(true, false, false, false));
        require(root.state() == RootState.PAUSED && action.stops().equals(List.of(ExitReason.PREEMPTED))
            && sim.ctx().leases().snapshot().isEmpty(), "SESSION preempts FlightKeeper and releases all flight leases");
        sim.tick(root, SimulatedSensors.flight(true, null));
        require(root.state() == RootState.RUNNING && action.starts() == 2,
            "FlightKeeper can resume its guarded child after SESSION clears");
        sim.tick(root, SimulatedSensors.session(false, false, false, true));
        require(root.state() == RootState.STOPPED && root.stopReport().orElseThrow().reason() == ExitReason.DEAD
            && action.cleanups() == 2 && sim.ctx().leases().snapshot().isEmpty(),
            "DEATH stops the flight tree with the user's immediate death report policy");
    }

    private void lifecycle() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("lifecycle-flight");
        FlightKeeperRoutine routine = new FlightKeeperRoutine(action);
        HookEntry entry = prepare(sim, routine);
        RoutineContext context = new RoutineContext(sim.ctx());
        List<HookEntry> entries = routine.entries(context);
        require(entries.size() == 1 && routine.entries(context) == entries,
            "FlightKeeper has one stable principal entry per routine context");
        sim.tick(entry.guard(), SimulatedSensors.flight(true, null));
        entry.guard().halt(sim.ctx(), ExitReason.STOPPED);
        StopReport report = new StopReport(ExitReason.STOPPED, "flight check stopped", "bot:lab", sim.ctx().tick(), sim.ctx().nowMs());
        routine.onUninstall(report);
        require(routine.status().state().equals("stopped") && routine.status().detail().equals(report.detail()),
            "FlightKeeper exposes the final stop report through the Routine contract");
        routine.onInstall(context);
        require(routine.entries(context) == entries && routine.status().state().equals("ready"),
            "FlightKeeper reinstall keeps entries and clears its stopped status");
        boolean rejected = false;
        try {
            routine.entries(new RoutineContext(new Simulation().ctx()));
        } catch (IllegalStateException ex) {
            rejected = ex.getMessage().contains("another context");
        }
        require(rejected, "FlightKeeper rejects sharing its nodes between engines");
    }
}
