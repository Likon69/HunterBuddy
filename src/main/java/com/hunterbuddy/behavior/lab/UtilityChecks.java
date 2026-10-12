package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.UtilityOption;
import com.hunterbuddy.behavior.nodes.UtilitySelector;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class UtilityChecks {
    private final List<String> checks = new ArrayList<>();

    private UtilityChecks() {
    }

    public static List<String> run() {
        UtilityChecks check = new UtilityChecks();
        check.selection();
        check.hysteresis();
        check.penalties();
        check.targetChanges();
        check.resourcesAndFaults();
        return List.copyOf(check.checks);
    }

    private UtilitySelector prepare(UtilitySelector selector) {
        TreeValidator.validate(selector);
        TreeValidator.bind(selector, "utility", "bot:lab", Hook.BOT);
        return selector;
    }

    private Node bounded(Node node) {
        return new Timeout(500, node);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("utility check failed: " + name);
        checks.add(name);
    }

    private void selection() {
        Simulation sim = new Simulation();
        SimulatedAction first = new SimulatedAction("first");
        SimulatedAction second = new SimulatedAction("second");
        UtilitySelector selector = prepare(new UtilitySelector("tasks",
            new UtilityOption(bounded(first), ctx -> 0.5, ctx -> "a"),
            new UtilityOption(bounded(second), ctx -> 0.5, ctx -> "b")));
        require(sim.tick(selector) instanceof Running && first.starts() == 1 && second.starts() == 0,
            "utility ties preserve declaration order");
        require(selector.scores().size() == 2 && selector.selectedPath().equals(selector.children().getFirst().path()),
            "utility exposes immutable scores and selected path");
        selector.halt(sim.ctx(), ExitReason.STOPPED);

        selector = prepare(new UtilitySelector("empty"));
        require(sim.tick(selector) instanceof Failure failure && failure.code() == FailureCode.NO_OPTION,
            "empty utility fails NO_OPTION");
        SimulatedAction invalid = new SimulatedAction("invalid");
        SimulatedAction eligible = new SimulatedAction("eligible", ctx -> Success.INSTANCE);
        selector = prepare(new UtilitySelector("nonfinite",
            new UtilityOption(bounded(invalid), ctx -> Double.POSITIVE_INFINITY, ctx -> "invalid"),
            new UtilityOption(bounded(new SimulatedAction("nan")), ctx -> Double.NaN, ctx -> "nan"),
            new UtilityOption(bounded(eligible), ctx -> 0.1, ctx -> "eligible")));
        require(sim.tick(selector) instanceof Success && invalid.starts() == 0 && eligible.starts() == 1,
            "utility ignores nonfinite scores and propagates Success");
    }

    private void hysteresis() {
        Simulation sim = new Simulation();
        double[] score = {0.2};
        SimulatedAction current = new SimulatedAction("current");
        SimulatedAction challenger = new SimulatedAction("challenger");
        UtilitySelector selector = prepare(new UtilitySelector("tasks",
            new UtilityOption(bounded(current), ctx -> 0.5, ctx -> "current"),
            new UtilityOption(bounded(challenger), ctx -> score[0], ctx -> "challenger")));
        sim.tick(selector);
        score[0] = 0.9;
        for (int i = 0; i < 99; i++) sim.tick(selector);
        require(challenger.starts() == 0 && current.ticks() == 100, "utility holds current choice for 100 ticks");
        score[0] = 0.7;
        sim.tick(selector);
        require(challenger.starts() == 0, "utility requires strictly more than 0.2 score gain");
        score[0] = 0.9;
        sim.tick(selector);
        require(challenger.starts() == 1 && current.stops().equals(List.of(ExitReason.PREEMPTED)),
            "utility preempts and starts better task in same tick");
        selector.halt(sim.ctx(), ExitReason.PAUSED);
        score[0] = 0.1;
        sim.tick(selector);
        require(current.starts() == 2, "utility rescores after pause");
        selector.halt(sim.ctx(), ExitReason.STOPPED);

        sim = new Simulation();
        double[] enabled = {1};
        current = new SimulatedAction("no-longer-eligible");
        challenger = new SimulatedAction("fallback");
        selector = prepare(new UtilitySelector("eligibility",
            new UtilityOption(bounded(current), ctx -> enabled[0], ctx -> "current"),
            new UtilityOption(bounded(challenger), ctx -> 0.1, ctx -> "fallback")));
        sim.tick(selector);
        enabled[0] = 0;
        sim.tick(selector);
        require(current.stops().equals(List.of(ExitReason.GUARD_LOST)) && challenger.starts() == 1,
            "utility stops ineligible task without waiting for hold");
        selector.halt(sim.ctx(), ExitReason.STOPPED);
    }

    private void penalties() {
        Simulation sim = new Simulation();
        SimulatedAction refused = new SimulatedAction("refused", ctx -> new Failure(FailureCode.REFUSED, "refused"));
        SimulatedAction fallback = new SimulatedAction("fallback");
        String[] target = {"a"};
        UtilitySelector selector = prepare(new UtilitySelector("tasks",
            new UtilityOption(bounded(refused), ctx -> 1, ctx -> target[0]),
            new UtilityOption(bounded(fallback), ctx -> 0.1, ctx -> "fallback")));
        require(sim.tick(selector) instanceof Running && refused.starts() == 1 && fallback.starts() == 1,
            "utility failure falls back in same tick");
        require(selector.scores().getFirst().failures() == 1 && !selector.scores().getFirst().eligible(),
            "utility stores target-specific failure penalty");
        selector.halt(sim.ctx(), ExitReason.PREEMPTED);
        sim.tick(selector);
        require(refused.starts() == 1, "utility penalty survives preemption");
        selector.halt(sim.ctx(), ExitReason.STOPPED);
        selector.resetMemory();
        sim.tick(selector);
        require(refused.starts() == 1, "utility node reset never clears OWNER penalty");
        selector.halt(sim.ctx(), ExitReason.STOPPED);
        target[0] = "b";
        sim.tick(selector);
        require(refused.starts() == 2 && selector.scores().getFirst().target().equals("b"),
            "utility new target has independent failure history");
        selector.halt(sim.ctx(), ExitReason.STOPPED);
        target[0] = "a";
        sim.tick(selector);
        require(refused.starts() == 2, "utility old target retains penalty after target change");
        selector.halt(sim.ctx(), ExitReason.STOPPED);
        TreeValidator.bind(selector, "utility", "bot:other", Hook.BOT);
        sim.tick(selector);
        require(refused.starts() == 3, "utility penalty isolated by owner");
        selector.halt(sim.ctx(), ExitReason.STOPPED);

        sim = new Simulation();
        refused = new SimulatedAction("still-positive", ctx -> new Failure(FailureCode.NOT_CONFIRMED, "missing"));
        selector = prepare(new UtilitySelector("bounded-rescore",
            new UtilityOption(bounded(refused), ctx -> 1, ctx -> "a")));
        require(sim.tick(selector) instanceof Running && refused.starts() == 1,
            "utility tries each option at most once per tick despite positive score");
        require(sim.ctx().evidenceSequence() == 0, "utility failure and reselection manufacture no progress");
    }

    private void targetChanges() {
        Simulation sim = new Simulation();
        String[] target = {"a"};
        SimulatedAction action = new SimulatedAction("target-work");
        UtilitySelector selector = prepare(new UtilitySelector("tasks",
            new UtilityOption(bounded(action), ctx -> 1, ctx -> target[0])));
        sim.tick(selector);
        target[0] = "b";
        sim.tick(selector);
        require(action.starts() == 2 && action.stops().equals(List.of(ExitReason.ABORTED)),
            "utility target change stops old activation before new work");
        selector.halt(sim.ctx(), ExitReason.STOPPED);

        int[] visits = {0};
        target[0] = "first";
        selector = prepare(new UtilitySelector("resumable-target",
            new UtilityOption(new Sequence("mission",
                bounded(new SimulatedAction("arrive", ctx -> {
                    visits[0]++;
                    return Success.INSTANCE;
                })), bounded(new SimulatedAction("work"))).resumable(), ctx -> 1, ctx -> target[0])));
        sim.tick(selector);
        selector.halt(sim.ctx(), ExitReason.PREEMPTED);
        sim.tick(selector);
        require(visits[0] == 1, "utility same target preserves resumable sequence after preemption");
        selector.halt(sim.ctx(), ExitReason.PREEMPTED);
        target[0] = "second";
        sim.tick(selector);
        require(visits[0] == 2, "utility new target restarts sequence after external preemption");
        target[0] = "third";
        sim.tick(selector);
        require(visits[0] == 3, "utility active target change never skips completed navigation stage");
        selector.halt(sim.ctx(), ExitReason.STOPPED);

        action = new SimulatedAction("changes-target-before-failure", ctx -> {
            target[0] = "next";
            return new Failure(FailureCode.NOT_CONFIRMED, "missing");
        });
        target[0] = "original";
        selector = prepare(new UtilitySelector("failure-target",
            new UtilityOption(bounded(action), ctx -> 1, ctx -> target[0])));
        sim.tick(selector);
        require(selector.scores().getFirst().target().equals("next") && selector.scores().getFirst().failures() == 0,
            "utility penalizes executed target rather than newly proposed target");
        target[0] = "original";
        sim.tick(selector);
        require(action.starts() == 1 && selector.scores().getFirst().failures() == 1,
            "utility original failed target remains excluded");
    }

    private void resourcesAndFaults() {
        Simulation sim = new Simulation();
        double[] currentScore = {1};
        SimulatedAction first = new SimulatedAction("first");
        SimulatedAction second = new SimulatedAction("second");
        UtilitySelector selector = prepare(new UtilitySelector("resources",
            new UtilityOption(new WithLease(Set.of(Resource.SLOT), LeasePolicy.FAIL_FAST, bounded(first)),
                ctx -> currentScore[0], ctx -> "a"),
            new UtilityOption(new WithLease(Set.of(Resource.SLOT), LeasePolicy.FAIL_FAST, bounded(second)),
                ctx -> 0.5, ctx -> "b")));
        sim.tick(selector);
        currentScore[0] = 0;
        require(sim.tick(selector) instanceof Running && first.cleanups() == 1 && second.starts() == 1
            && sim.ctx().leases().snapshot().size() == 1, "utility releases old lease before starting replacement");
        selector.halt(sim.ctx(), ExitReason.STOPPED);
        require(sim.ctx().leases().snapshot().isEmpty(), "utility stop releases active resources");

        boolean[] faulty = {false};
        first = new SimulatedAction("faulted-work");
        selector = prepare(new UtilitySelector("score-fault",
            new UtilityOption(new WithLease(Set.of(Resource.SLOT), LeasePolicy.WAIT, bounded(first)), ctx -> {
                if (faulty[0]) throw new IllegalStateException("score fault");
                return 1;
            }, ctx -> "a")));
        sim.tick(selector);
        faulty[0] = true;
        boolean thrown = false;
        try {
            sim.tick(selector);
        } catch (IllegalStateException ex) {
            thrown = "score fault".equals(ex.getMessage());
        }
        require(thrown && !selector.active() && first.cleanups() == 1 && sim.ctx().leases().snapshot().isEmpty(),
            "utility score exception cleans active task and lease");

        currentScore[0] = 1;
        first = new SimulatedAction("cleanup-fault", ctx -> Running.WORKING, (ctx, reason) -> {
            throw new IllegalStateException("cleanup fault");
        });
        second = new SimulatedAction("replacement");
        selector = prepare(new UtilitySelector("cleanup-fault",
            new UtilityOption(new WithLease(Set.of(Resource.SLOT), LeasePolicy.FAIL_FAST, bounded(first)),
                ctx -> currentScore[0], ctx -> "a"),
            new UtilityOption(bounded(second), ctx -> 0.5, ctx -> "b")));
        sim.tick(selector);
        currentScore[0] = 0;
        thrown = false;
        try {
            sim.tick(selector);
        } catch (IllegalStateException ex) {
            thrown = "cleanup fault".equals(ex.getMessage());
        }
        require(thrown && !selector.active() && first.cleanups() == 1 && second.starts() == 0
            && sim.ctx().leases().snapshot().isEmpty(), "utility cleanup fault releases lease without starting replacement");
    }
}
