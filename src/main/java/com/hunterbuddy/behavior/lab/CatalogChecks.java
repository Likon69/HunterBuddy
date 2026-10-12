package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.nodes.Budget;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.ForceSuccess;
import com.hunterbuddy.behavior.nodes.If;
import com.hunterbuddy.behavior.nodes.ReactiveSequence;
import com.hunterbuddy.behavior.nodes.Repeat;
import com.hunterbuddy.behavior.nodes.Retry;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.Subtree;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.While;
import java.util.ArrayList;
import java.util.List;

public final class CatalogChecks {
    private final List<String> checks = new ArrayList<>();

    private CatalogChecks() {
    }

    public static List<String> run() {
        CatalogChecks check = new CatalogChecks();
        check.conditions();
        check.reactiveSequence();
        check.forceSuccess();
        check.retry();
        check.repeat();
        check.subtrees();
        check.values();
        return List.copyOf(check.checks);
    }

    private Node prepare(Node node) {
        TreeValidator.validate(node);
        TreeValidator.bind(node, "catalog", "bot:lab", Hook.BOT);
        return node;
    }

    private Node bounded(Node node) {
        return new Timeout(100, node);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("catalog check failed: " + name);
        checks.add(name);
    }

    private boolean failed(NodeResult result, FailureCode code) {
        return result instanceof Failure failure && failure.code() == code;
    }

    private void conditions() {
        Simulation sim = new Simulation();
        boolean[] allowed = {false};
        Condition condition = new Condition("allowed", sensors -> allowed[0]);
        SimulatedAction action = new SimulatedAction("work");
        Node conditional = prepare(new If(condition, bounded(action)));
        require(failed(sim.tick(conditional), FailureCode.CONDITION_FALSE) && action.starts() == 0,
            "If refuses before starting child");
        allowed[0] = true;
        require(sim.tick(conditional) instanceof Running, "If admits at activation");
        allowed[0] = false;
        require(sim.tick(conditional) instanceof Running && action.starts() == 1,
            "If does not recheck during activation");
        conditional.halt(sim.ctx(), ExitReason.STOPPED);
        require(action.cleanups() == 1, "If halts child and cleans activation");
        require(failed(sim.tick(conditional), FailureCode.CONDITION_FALSE), "If rechecks on next activation");

        action = new SimulatedAction("guarded-work");
        Node loop = prepare(new While(condition, bounded(action)));
        require(failed(sim.tick(loop), FailureCode.GUARD_LOST) && action.starts() == 0,
            "While false never starts child");
        allowed[0] = true;
        sim.tick(loop);
        allowed[0] = false;
        require(failed(sim.tick(loop), FailureCode.GUARD_LOST)
            && action.stops().equals(List.of(ExitReason.GUARD_LOST)), "While loses guard and stops child once");
    }

    private void reactiveSequence() {
        Simulation sim = new Simulation();
        boolean[] allowed = {true};
        int[] completed = {0};
        SimulatedAction first = new SimulatedAction("first", ctx -> {
            completed[0]++;
            return Success.INSTANCE;
        });
        SimulatedAction second = new SimulatedAction("second");
        Node sequence = prepare(new ReactiveSequence("reactive",
            new Condition("allowed", sensors -> allowed[0]), bounded(first), bounded(second)));
        require(sim.tick(sequence) instanceof Running && completed[0] == 1, "ReactiveSequence advances same tick");
        sim.tick(sequence);
        require(completed[0] == 1 && second.ticks() == 2, "ReactiveSequence keeps running index");
        allowed[0] = false;
        require(failed(sim.tick(sequence), FailureCode.GUARD_LOST)
            && second.stops().equals(List.of(ExitReason.GUARD_LOST)), "ReactiveSequence rechecks prefix and halts work");
        allowed[0] = true;
        sim.tick(sequence);
        require(completed[0] == 2, "ReactiveSequence restarts from beginning after guard loss");
        sequence.halt(sim.ctx(), ExitReason.PAUSED);
        sim.tick(sequence);
        require(completed[0] == 3, "ReactiveSequence restarts after pause");
        sequence.halt(sim.ctx(), ExitReason.STOPPED);
        require(sim.tick(prepare(new ReactiveSequence("empty"))) instanceof Success, "empty ReactiveSequence succeeds");
    }

    private void forceSuccess() {
        Simulation sim = new Simulation();
        Node running = prepare(new ForceSuccess(bounded(new SimulatedAction("running"))));
        require(sim.tick(running) instanceof Running, "ForceSuccess preserves Running");
        running.halt(sim.ctx(), ExitReason.STOPPED);
        Node timeout = prepare(new ForceSuccess(new Timeout(1, new SimulatedAction("timeout")))
            .only(FailureCode.TIMEOUT));
        sim.tick(timeout);
        require(sim.tick(timeout) instanceof Success, "ForceSuccess masks selected terminal failure");
        Node excluded = prepare(new ForceSuccess(bounded(new SimulatedAction("refused",
            ctx -> new Failure(FailureCode.REFUSED, "refused")))).only(FailureCode.TIMEOUT));
        require(failed(sim.tick(excluded), FailureCode.REFUSED), "ForceSuccess preserves excluded failure");
        Node fault = prepare(new ForceSuccess(bounded(new SimulatedAction("fault", ctx -> {
            throw new IllegalStateException("injected fault");
        }))));
        boolean thrown = false;
        try {
            sim.tick(fault);
        } catch (IllegalStateException ex) {
            thrown = "injected fault".equals(ex.getMessage());
        }
        require(thrown && !fault.active(), "ForceSuccess never masks runtime exception");
    }

    private void retry() {
        Simulation sim = new Simulation();
        int[] attempts = {0};
        SimulatedAction action = new SimulatedAction("retry-work", ctx -> ++attempts[0] < 3
            ? new Failure(FailureCode.NOT_CONFIRMED, "not confirmed") : Success.INSTANCE);
        Node retry = prepare(new Retry(3, 2, bounded(action)));
        sim.tick(retry);
        sim.tick(retry);
        retry.halt(sim.ctx(), ExitReason.PREEMPTED);
        sim.advance(10_000);
        require(sim.tick(retry) instanceof Running && attempts[0] == 1, "Retry backoff freezes while preempted");
        sim.tick(retry);
        require(attempts[0] == 2, "Retry starts second attempt after active backoff");
        retry.halt(sim.ctx(), ExitReason.PAUSED);
        sim.tick(retry);
        sim.tick(retry);
        require(action.starts() == 2, "Retry increases backoff after second failed attempt");
        sim.tick(retry);
        sim.tick(retry);
        require(sim.tick(retry) instanceof Success && action.starts() == 3 && action.cleanups() == 3,
            "Retry preserves attempts across pause and cleans each attempt");

        sim = new Simulation();
        action = new SimulatedAction("always-fails", ctx -> new Failure(FailureCode.NOT_CONFIRMED, "missing"));
        retry = prepare(new Retry(2, 0, bounded(action)));
        sim.tick(retry);
        retry.halt(sim.ctx(), ExitReason.PREEMPTED);
        require(failed(sim.tick(retry), FailureCode.NOT_CONFIRMED) && action.starts() == 2,
            "Retry never refunds failed attempts on preemption");

        for (FailureCode code : List.of(FailureCode.BUDGET, FailureCode.LOOP, FailureCode.PREEMPTED, FailureCode.FAULT)) {
            action = new SimulatedAction("excluded-" + code, ctx -> new Failure(code, "excluded"));
            retry = prepare(new Retry(3, 0, bounded(action)));
            require(failed(sim.tick(retry), code) && action.starts() == 1, "Retry default excludes " + code);
        }
        action = new SimulatedAction("filtered", ctx -> new Failure(FailureCode.REFUSED, "refused"));
        retry = prepare(new Retry(3, 0, bounded(action)).on(FailureCode.NOT_CONFIRMED));
        require(failed(sim.tick(retry), FailureCode.REFUSED) && action.starts() == 1, "Retry respects explicit filter");

        action = new SimulatedAction("budgeted", ctx -> new Failure(FailureCode.NOT_CONFIRMED, "missing"));
        retry = prepare(new Retry(3, 0, new Budget("requests", 1, bounded(action))));
        sim.tick(retry);
        require(failed(sim.tick(retry), FailureCode.BUDGET) && action.starts() == 1, "Retry preserves child Budget ledger");

        sim = new Simulation();
        retry = prepare(new Timeout(2, new Retry(20, 0, bounded(new SimulatedAction("persistent-failure",
            ctx -> new Failure(FailureCode.NOT_CONFIRMED, "missing"))))));
        sim.tick(retry);
        retry.halt(sim.ctx(), ExitReason.PAUSED);
        sim.tick(retry);
        require(failed(sim.tick(retry), FailureCode.TIMEOUT), "Retry cannot refund enclosing Timeout");
    }

    private void repeat() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("repeated", ctx -> Success.INSTANCE);
        Node repeat = prepare(new Repeat(3, bounded(action)));
        require(sim.tick(repeat) instanceof Running, "Repeat yields between iterations");
        repeat.halt(sim.ctx(), ExitReason.PREEMPTED);
        sim.tick(repeat);
        require(sim.tick(repeat) instanceof Success && action.starts() == 3, "Repeat keeps completed count after preemption");
        action = new SimulatedAction("never-start");
        require(sim.tick(prepare(new Repeat(0, bounded(action)))) instanceof Success && action.starts() == 0,
            "zero Repeat never starts child");
        repeat = prepare(new Repeat(3, bounded(new SimulatedAction("failure",
            ctx -> new Failure(FailureCode.REFUSED, "refused")))));
        require(failed(sim.tick(repeat), FailureCode.REFUSED), "Repeat propagates child failure");
        repeat = prepare(new Repeat(2, new Timeout(1, new SimulatedAction("running"))));
        sim.tick(repeat);
        require(failed(sim.tick(repeat), FailureCode.TIMEOUT), "Repeat cannot hide child timeout");

        action = new SimulatedAction("bounded-repeated", ctx -> Success.INSTANCE);
        repeat = prepare(new Repeat(10, bounded(action)));
        NodeResult result = Running.WORKING;
        for (int i = 0; i < 10 && result instanceof Running; i++) result = sim.tick(repeat);
        require(failed(result, FailureCode.LOOP) && action.starts() == 5,
            "Repeat never treats new iteration as proof of progress");
    }

    private void subtrees() {
        Simulation sim = new Simulation();
        int[] made = {0};
        Subtree first = new Subtree("first", () -> {
            made[0]++;
            return new Sequence("body", new Succeed());
        });
        Subtree second = new Subtree("second", () -> {
            made[0]++;
            return new Sequence("body", new Succeed());
        });
        Node tree = prepare(new Sequence("sites", first, second));
        require(first.child() != second.child() && made[0] == 2 && sim.tick(tree) instanceof Success,
            "Subtree instantiates distinct body per site");
        sim.tick(tree);
        require(made[0] == 2, "Subtree keeps body across activations for stable budgets");
        Node shared = new Succeed();
        boolean rejected = false;
        try {
            TreeValidator.validate(new Sequence("shared", new Subtree("a", () -> shared), new Subtree("b", () -> shared)));
        } catch (IllegalArgumentException ex) {
            rejected = ex.getMessage().contains("double parent");
        }
        require(rejected, "Subtree shared factory result rejected by validator");
    }

    private void values() {
        Simulation sim = new Simulation();
        Key<Integer> owner = new Key<>("amount", Integer.class, Scope.OWNER, false);
        Node set = prepare(new SetValue<>(owner, ctx -> 40));
        require(sim.tick(set) instanceof Success && sim.ctx().bb().get("bot:lab", owner).orElseThrow() == 40,
            "SetValue writes typed value in bound owner");
        require(sim.ctx().bb().get("other", owner).isEmpty() && sim.ctx().evidenceSequence() == 0,
            "SetValue isolates owners and emits no progress proof");
        Key<Integer> task = new Key<>("temporary", Integer.class, Scope.TASK, false);
        Node scoped = prepare(new TaskScope(new Sequence("task", new SetValue<>(task, ctx -> 1),
            bounded(new SimulatedAction("read", ctx -> ctx.bb().get(ctx.owner(), task).orElseThrow() == 1
                ? Success.INSTANCE : new Failure(FailureCode.NOT_CONFIRMED, "missing value"))))));
        require(sim.tick(scoped) instanceof Success, "SetValue supports scoped task memory");
    }
}
