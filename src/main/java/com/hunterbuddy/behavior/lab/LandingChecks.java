package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Landing;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Subtree;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class LandingChecks {
    private final List<String> checks = new ArrayList<>();

    private LandingChecks() {
    }

    public static List<String> run() {
        LandingChecks check = new LandingChecks();
        check.results();
        check.lifecycle();
        check.timeout();
        check.rocketValidation();
        check.validationEntries();
        check.leaseReservation();
        return List.copyOf(check.checks);
    }

    private Node prepare(Node node) {
        TreeValidator.validate(node);
        TreeValidator.bind(node, "landing-check", "bot:lab", Hook.BOT);
        return node;
    }

    private Node bounded(Node node) {
        return new Timeout(100, node);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("landing check failed: " + name);
        checks.add(name);
    }

    private boolean rejected(Runnable validation, String message) {
        try {
            validation.run();
            return false;
        } catch (IllegalArgumentException ex) {
            return ex.getMessage().contains(message);
        }
    }

    private void results() {
        Simulation sim = new Simulation();
        Running running = new Running("landing in progress");
        SimulatedAction action = new SimulatedAction("work", ctx -> running);
        Node landing = prepare(new Landing(bounded(action)));
        require(sim.tick(landing) == running && action.starts() == 1,
            "Landing preserves child Running and reason");
        require(sim.ctx().evidenceSequence() == 0, "Landing emits no progress evidence");
        landing.halt(sim.ctx(), ExitReason.STOPPED);

        action = new SimulatedAction("done", ctx -> Success.INSTANCE);
        landing = prepare(new Landing(bounded(action)));
        require(sim.tick(landing) == Success.INSTANCE && !landing.active() && action.cleanups() == 1,
            "Landing propagates Success and completes child once");

        Failure failure = new Failure(FailureCode.REFUSED, "landing refused");
        action = new SimulatedAction("refused", ctx -> failure);
        landing = prepare(new Landing(bounded(action)));
        require(sim.tick(landing) == failure && action.stops().equals(List.of(ExitReason.FAILED)),
            "Landing preserves child failure code and reason");
    }

    private void lifecycle() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("leased-work");
        Node landing = prepare(new Landing(new WithLease(Set.of(Resource.MOVEMENT), LeasePolicy.WAIT, bounded(action))));
        sim.tick(landing);
        require(sim.ctx().leases().holder(Resource.MOVEMENT).isPresent(), "Landing child acquires its lease");
        landing.halt(sim.ctx(), ExitReason.PREEMPTED);
        landing.halt(sim.ctx(), ExitReason.STOPPED);
        require(action.cleanups() == 1 && action.stops().equals(List.of(ExitReason.PREEMPTED))
            && sim.ctx().leases().snapshot().isEmpty(), "Landing preemption cleans child and releases lease exactly once");
        require(sim.tick(landing) instanceof Running && action.starts() == 2,
            "Landing starts a fresh child activation after preemption");
        landing.halt(sim.ctx(), ExitReason.STOPPED);
        require(action.cleanups() == 2 && sim.ctx().leases().snapshot().isEmpty(),
            "Landing stop releases the reacquired lease");

        boolean[] allowed = {true};
        SimulatedAction guarded = new SimulatedAction("guarded-work");
        Node guard = prepare(new Guard(new Condition("allowed", sensors -> allowed[0]),
            new Landing(new WithLease(Set.of(Resource.MOVEMENT), LeasePolicy.WAIT, bounded(guarded)))));
        sim.tick(guard);
        allowed[0] = false;
        require(sim.tick(guard) instanceof Failure failure && failure.code() == FailureCode.GUARD_LOST
            && guarded.stops().equals(List.of(ExitReason.GUARD_LOST)) && sim.ctx().leases().snapshot().isEmpty(),
            "Landing inherits guard loss cleanup and lease release");

        SimulatedAction faulty = new SimulatedAction("faulty-work", ctx -> {
            throw new IllegalStateException("injected landing fault");
        });
        Node fault = prepare(new Landing(new WithLease(Set.of(Resource.MOVEMENT), LeasePolicy.WAIT, bounded(faulty))));
        boolean thrown = false;
        try {
            sim.tick(fault);
        } catch (IllegalStateException ex) {
            thrown = "injected landing fault".equals(ex.getMessage());
        }
        require(thrown && !fault.active() && faulty.cleanups() == 1
            && faulty.stops().equals(List.of(ExitReason.FAULT)) && sim.ctx().leases().snapshot().isEmpty(),
            "Landing propagates faults and cleans the whole activation");
    }

    private void timeout() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("timed-work");
        Node landing = prepare(new Landing(new Timeout(2, action)));
        sim.tick(landing);
        landing.halt(sim.ctx(), ExitReason.PAUSED);
        sim.advance(10_000);
        require(sim.tick(landing) instanceof Running && action.ticks() == 2,
            "Landing pause preserves the child active timeout");
        require(sim.tick(landing) instanceof Failure failure && failure.code() == FailureCode.TIMEOUT
            && action.stops().equals(List.of(ExitReason.PAUSED, ExitReason.ABORTED)),
            "Landing cannot refund a child timeout after pause");
        landing.resetMemory();
        require(sim.tick(landing) instanceof Running, "Landing forwards explicit memory reset to child");
        landing.halt(sim.ctx(), ExitReason.STOPPED);
    }

    private void rocketValidation() {
        SimulatedRocketAction direct = new SimulatedRocketAction("direct-rocket");
        require(rejected(() -> TreeValidator.validate(new Landing(bounded(direct))), "rocket emission under Landing")
            && !direct.active() && direct.emissions() == 0, "Landing rejects rocket emission before activation");

        SimulatedRocketAction nested = new SimulatedRocketAction("nested-rocket");
        Node tree = bounded(new Landing(new Subtree("body", () -> new Sequence("steps", new Succeed(),
            new Guard(new Condition("never", sensors -> false), new Landing(nested))))));
        require(rejected(() -> TreeValidator.validate(tree), "rocket emission under Landing") && nested.emissions() == 0,
            "Landing rejection crosses subtrees composites guards and nested markers even with a false guard");

        Simulation sim = new Simulation();
        SimulatedRocketAction before = new SimulatedRocketAction("before-landing");
        SimulatedRocketAction after = new SimulatedRocketAction("after-landing");
        Node siblings = prepare(new Sequence("siblings", bounded(before), new Landing(new Succeed()), bounded(after)));
        require(sim.tick(siblings) instanceof Success && before.emissions() == 1 && after.emissions() == 1,
            "Landing restriction does not leak to preceding or following siblings");

        require(rejected(() -> TreeValidator.validate(new Landing(new SimulatedAction("unbounded"))),
            "Action without Timeout/ProgressGuard"), "Landing never substitutes for an Action bound");
        require(rejected(() -> TreeValidator.validate(new SimulatedRocketAction("unbounded-rocket")),
            "Action without Timeout/ProgressGuard"), "RocketAction retains mandatory Action bounds");
        Node shared = new Landing(new Succeed());
        require(rejected(() -> TreeValidator.validate(new Sequence("shared", shared, shared)), "double parent"),
            "Landing retains duplicate parent rejection");
    }

    private void validationEntries() {
        require(rejected(() -> TreeValidator.validate(new Landing(bounded(new SimulatedRocketAction("root-rocket"))),
            new Succeed()), "rocket emission under Landing"), "Root and recovery validation rejects rockets in root Landing");
        require(rejected(() -> TreeValidator.validate(new Succeed(),
            new Landing(bounded(new SimulatedRocketAction("recovery-rocket")))), "rocket emission under Landing"),
            "Root and recovery validation rejects rockets in recovery Landing");

        Simulation sim = new Simulation();
        SimulatedRocketAction recovery = new SimulatedRocketAction("allowed-recovery");
        Node root = new Landing(new Succeed());
        Node outside = bounded(recovery);
        TreeValidator.validate(root, outside);
        TreeValidator.bind(outside, "recovery", "core:recovery", Hook.RECOVERY);
        require(sim.tick(outside) instanceof Success && recovery.emissions() == 1,
            "Root Landing does not impose its restriction on separate recovery");

        Node blocked = new Landing(bounded(new SimulatedRocketAction("hook-rocket")));
        Guard guard = new Guard(new Condition("always", sensors -> true), blocked);
        HookEntry entry = new HookEntry(Hook.BOT, "bot:lab", Contributor.BOT, 0, guard, true, blocked);
        require(rejected(() -> TreeValidator.validate(List.of(entry)), "rocket emission under Landing"),
            "Hook entry validation enforces Landing prohibition");
    }

    private void leaseReservation() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("reserve-only");
        Node landing = prepare(new Landing(new WithLease(Set.of(Resource.ROCKETS), LeasePolicy.WAIT, bounded(action))));
        require(sim.tick(landing) instanceof Running && action.starts() == 1
            && sim.ctx().leases().holder(Resource.ROCKETS).isPresent()
            && sim.ctx().leases().holder(Resource.SLOT).isPresent()
            && sim.ctx().leases().holder(Resource.INTERACT).isPresent(),
            "Landing permits ROCKETS reservation and its implications without rocket emission");
        landing.halt(sim.ctx(), ExitReason.STOPPED);
        require(sim.ctx().leases().snapshot().isEmpty() && action.cleanups() == 1,
            "Landing releases ROCKETS reservation and implied leases on stop");
    }
}
