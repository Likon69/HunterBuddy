package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.CandidatePicker;
import com.hunterbuddy.behavior.core.CandidateSelection;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.TreeRoot;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.MarkFailed;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.UtilityOption;
import com.hunterbuddy.behavior.nodes.UtilitySelector;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PickerIntegrationChecks {
    private final CandidatePicker<String> picker = new CandidatePicker<>();
    private final List<String> checks = new ArrayList<>();

    private PickerIntegrationChecks() {
    }

    public static List<String> run() {
        PickerIntegrationChecks check = new PickerIntegrationChecks();
        check.waitAndStop();
        check.sameTickFallback();
        check.currentTarget();
        check.exhausted();
        return List.copyOf(check.checks);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("picker integration check failed: " + name);
        checks.add(name);
    }

    private UtilitySelector prepare(UtilitySelector selector) {
        TreeValidator.validate(selector);
        TreeValidator.bind(selector, "picker-integration", "bot:lab", Hook.BOT);
        return selector;
    }

    private UtilityOption option(Node node, List<String> candidates) {
        return UtilityOption.withCandidates(node, ctx -> 1, ctx -> candidates, value -> 1, value -> value);
    }

    private TreeRoot root(Simulation sim, UtilitySelector selector) {
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalStateException(message); });
        Guard guard = new Guard(new Condition("always", sensors -> true), selector);
        hooks.insert(new HookEntry(Hook.BOT, "bot:lab", Contributor.BOT, 0, guard, false, selector));
        hooks.applyPendingChanges(sim.ctx());
        TreeRoot root = new TreeRoot(sim.ctx(), hooks,
            new Recovery(new Fail(FailureCode.NO_OPTION, "none"), new Succeed(), message -> { }));
        root.start();
        return root;
    }

    private void waitAndStop() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("mine", ctx -> new Failure(FailureCode.NOT_CONFIRMED, "missing"));
        UtilitySelector selector = new UtilitySelector("mining", option(new Timeout(100, action), List.of("block:a")));
        TreeRoot root = root(sim, selector);
        sim.tick(root, SimulatedSensors.idle());
        for (int i = 0; i < 599; i++) sim.tick(root, SimulatedSensors.idle());
        require(action.starts() == 1 && root.state() == RootState.RUNNING && root.recoveryLevel() == 0,
            "picker wait with zero score holds BOT for full first 30 seconds");
        require(selector.active() && selector.scores().getFirst().waiting() && selector.scores().getFirst().wanted(),
            "wanted task preserves picker deadline while candidate absent");
        sim.tick(root, SimulatedSensors.idle());
        require(action.starts() == 2, "picker retries at first exact deadline");
        for (int i = 0; i < 1_199; i++) sim.tick(root, SimulatedSensors.idle());
        require(action.starts() == 2 && root.state() == RootState.RUNNING && root.recoveryLevel() == 0,
            "picker second 60 second wait never falls into recovery");
        sim.tick(root, SimulatedSensors.idle());
        require(action.starts() == 3 && root.state() == RootState.STOPPED,
            "picker final exhaustion stops bot immediately after third failure");
        require(sim.ring().snapshot().stream().anyMatch(event -> event.kind() == Kind.BOT
            && event.reason().equals("stop") && event.detail().equals("mining: no remaining target")),
            "picker exhaustion emits correct stop reason");
        require(sim.ctx().evidenceSequence() == 0, "picker waits and retries manufacture no progress");
    }

    private void sameTickFallback() {
        Simulation sim = new Simulation();
        List<String> visited = new ArrayList<>();
        SimulatedAction action = new SimulatedAction("mine", ctx -> {
            String target = ctx.bb().get(ctx.owner(), TickContext.CURRENT_TARGET).orElseThrow();
            visited.add(target);
            return target.equals("a") ? new Failure(FailureCode.NOT_CONFIRMED, "a failed") : Success.INSTANCE;
        });
        UtilitySelector selector = prepare(new UtilitySelector("mine", option(new Timeout(100, action), List.of("a", "b"))));
        require(sim.tick(selector) instanceof Success && visited.equals(List.of("a", "b")),
            "picker switches to next block within same tick");

        sim = new Simulation();
        SimulatedAction failing = new SimulatedAction("many", ctx -> new Failure(FailureCode.NOT_CONFIRMED, "failed"));
        selector = prepare(new UtilitySelector("many-targets", option(new Timeout(100, failing), List.of("a", "b", "c", "d", "e", "f"))));
        require(sim.tick(selector) instanceof Running && failing.starts() == 2,
            "same tick target fallback remains bounded and defers further targets without NO_OPTION");
        sim.tick(selector);
        sim.tick(selector);
        require(failing.starts() == 6, "Action loop ledger uses actual candidate ids instead of shared default");
        require(selector.scores().getFirst().waiting(), "all failed candidates wait rather than fail task");
    }

    private void currentTarget() {
        Simulation sim = new Simulation();
        Node work = new Timeout(100, new SimulatedAction("work"));
        UtilitySelector selector = prepare(new UtilitySelector("task", option(work, List.of("point:a", "point:b"))));
        sim.tick(selector);
        require(sim.ctx().bb().get("bot:lab", TickContext.CURRENT_TARGET).orElseThrow().equals("point:a"),
            "utility publishes exact picker id in OWNER before execution");
        selector.halt(sim.ctx(), ExitReason.ABORTED);
        Recovery recovery = new Recovery(new Succeed(), new Succeed(), message -> { });
        TreeValidator.bind(recovery, "recovery", "core", Hook.RECOVERY);
        recovery.taskOwner("bot:lab");
        for (int i = 0; i < 3; i++) recovery.next(sim.ctx());
        sim.tick(recovery);
        require(sim.ctx().bb().get("bot:lab", MarkFailed.failedKey("point:a")).orElse(false),
            "recovery level three marks actual selected target without manual CURRENT_TARGET setup");
        require(picker.pick(sim.ctx(), "bot:lab", work.path(), List.of("point:a", "point:b"),
            value -> 1, value -> value).orElseThrow().equals("point:b"),
            "picker excludes target marked by real recovery flow");
        CandidateSelection<Integer> numeric = new CandidateSelection<>(Optional.of(42), 0);
        require(numeric.map(value -> "block:" + value).candidate().orElseThrow().equals("block:42"),
            "candidate mapping preserves identifier conversion");
        CandidateSelection<Integer> pending = new CandidateSelection<>(Optional.empty(), 30_000);
        require(pending.map(Object::toString).retryAtMs() == 30_000 && !pending.map(Object::toString).exhausted(),
            "candidate mapping preserves absent target retry deadline");
    }

    private void exhausted() {
        Simulation sim = new Simulation();
        sim.ctx().bb().set("bot:lab", MarkFailed.failedKey("a"), true);
        SimulatedAction action = new SimulatedAction("never-start");
        UtilitySelector selector = new UtilitySelector("exhausted", option(new Timeout(100, action), List.of("a")));
        TreeRoot root = root(sim, selector);
        sim.tick(root, SimulatedSensors.idle());
        require(root.state() == RootState.STOPPED && action.starts() == 0 && root.recoveryLevel() == 0,
            "wanted exhausted picker stops directly instead of recovery retries");
    }
}
