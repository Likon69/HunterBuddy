package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TreeRoot;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.UtilityOption;
import com.hunterbuddy.behavior.nodes.UtilitySelector;
import java.util.ArrayList;
import java.util.List;

public final class UtilityRetryChecks {
    private final List<String> checks = new ArrayList<>();

    private UtilityRetryChecks() {
    }

    public static List<String> run() {
        UtilityRetryChecks check = new UtilityRetryChecks();
        check.delays();
        check.success();
        check.alternatives();
        check.stop();
        return List.copyOf(check.checks);
    }

    private UtilitySelector prepare(UtilitySelector selector) {
        TreeValidator.validate(selector);
        TreeValidator.bind(selector, "utility-retry", "bot:lab", Hook.BOT);
        return selector;
    }

    private Node bounded(Node node) {
        return new Timeout(100, node);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("utility retry check failed: " + name);
        checks.add(name);
    }

    private void advance(Simulation sim, long milliseconds) {
        for (long remaining = milliseconds; remaining > 0; remaining -= Math.min(remaining, 1_000)) {
            sim.advance(Math.min(remaining, 1_000));
        }
    }

    private SimulatedAction failing(String name) {
        return new SimulatedAction(name, ctx -> new Failure(FailureCode.NOT_CONFIRMED, "not confirmed"));
    }

    private void delays() {
        Simulation sim = new Simulation();
        List<String> stops = new ArrayList<>();
        sim.ctx().stopHandler(stops::add);
        SimulatedAction action = failing("deposit");
        UtilitySelector selector = prepare(new UtilitySelector("deposit",
            new UtilityOption(bounded(action), ctx -> 1, ctx -> "chest")));
        require(sim.tick(selector) instanceof Running && action.starts() == 1,
            "first utility failure waits without stopping bot");
        advance(sim, 29_900);
        sim.tick(selector);
        require(action.starts() == 1, "first utility penalty lasts full 30 seconds");
        require(sim.tick(selector) instanceof Running && action.starts() == 2,
            "first penalty expires and permits second attempt");
        require(selector.scores().getFirst().failures() == 2 && stops.isEmpty(),
            "penalty expiry preserves consecutive failure count");
        selector.halt(sim.ctx(), ExitReason.PREEMPTED);
        selector.resetMemory();
        advance(sim, 59_900);
        sim.tick(selector);
        require(action.starts() == 2, "second utility penalty survives reset and lasts full 60 seconds");
        require(sim.tick(selector) instanceof Failure && action.starts() == 3 && stops.size() == 1,
            "third failure abandons final target and signals stop");
        require(selector.scores().getFirst().abandoned(), "third failure marked abandoned");
        advance(sim, 3_600_000);
        sim.tick(selector);
        require(action.starts() == 3, "abandoned target never retried merely because time passes");
        require(sim.ctx().evidenceSequence() == 0, "penalty expiration creates no progress proof");
    }

    private void success() {
        Simulation sim = new Simulation();
        boolean[] succeeds = {false};
        SimulatedAction action = new SimulatedAction("work", ctx -> succeeds[0]
            ? Success.INSTANCE : new Failure(FailureCode.NOT_CONFIRMED, "missing"));
        UtilitySelector selector = prepare(new UtilitySelector("task",
            new UtilityOption(bounded(action), ctx -> 1, ctx -> "a")));
        sim.tick(selector);
        advance(sim, 30_000);
        succeeds[0] = true;
        require(sim.tick(selector) instanceof Success, "utility target can succeed after retry delay");
        require(sim.ctx().utilityFailures().get("bot:lab", selector.children().getFirst().path(), "a").consecutive() == 0,
            "utility Success clears target counter");
        succeeds[0] = false;
        sim.tick(selector);
        require(selector.scores().getFirst().failures() == 1
            && selector.scores().getFirst().retryAtMs() - sim.ctx().nowMs() == 30_000,
            "failure after Success starts again at first penalty");
    }

    private void alternatives() {
        Simulation sim = new Simulation();
        List<String> stops = new ArrayList<>();
        sim.ctx().stopHandler(stops::add);
        String[] target = {"a"};
        SimulatedAction action = failing("work");
        UtilitySelector selector = prepare(new UtilitySelector("task",
            new UtilityOption(bounded(action), ctx -> 1, ctx -> target[0])));
        sim.tick(selector);
        advance(sim, 30_000);
        sim.tick(selector);
        advance(sim, 60_000);
        target[0] = "b";
        sim.tick(selector);
        require(action.starts() == 3 && stops.isEmpty() && selector.scores().getFirst().failures() == 1,
            "utility failure history independent for a different target");
        target[0] = "a";
        sim.tick(selector);
        require(action.starts() == 4 && stops.size() == 1, "returning to old target preserves two consecutive failures");

        sim = new Simulation();
        SimulatedAction abandoned = failing("abandoned");
        SimulatedAction fallback = new SimulatedAction("fallback");
        selector = prepare(new UtilitySelector("alternatives",
            new UtilityOption(bounded(abandoned), ctx -> 2, ctx -> "chest"),
            new UtilityOption(bounded(fallback), ctx -> 0.1, ctx -> "other")));
        sim.tick(selector);
        advance(sim, 30_000);
        selector.halt(sim.ctx(), ExitReason.PREEMPTED);
        sim.tick(selector);
        advance(sim, 60_000);
        selector.halt(sim.ctx(), ExitReason.PREEMPTED);
        require(sim.tick(selector) instanceof Running && abandoned.starts() == 3 && fallback.starts() == 3,
            "third failure chooses available alternative without stopping bot");
        selector.halt(sim.ctx(), ExitReason.STOPPED);
    }

    private void stop() {
        Simulation sim = new Simulation();
        SimulatedAction action = failing("deposit");
        UtilitySelector selector = new UtilitySelector("delivery",
            new UtilityOption(bounded(action), ctx -> 1, ctx -> "chest"));
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalStateException(message); });
        Guard guard = new Guard(new Condition("always", sensors -> true), selector);
        hooks.insert(new HookEntry(Hook.BOT, "bot:lab", Contributor.BOT, 0, guard, false, selector));
        hooks.applyPendingChanges(sim.ctx());
        TreeRoot root = new TreeRoot(sim.ctx(), hooks,
            new Recovery(new Fail(FailureCode.NO_OPTION, "none"), new Succeed(), message -> { }));
        root.start();
        sim.tick(root, SimulatedSensors.idle());
        advance(sim, 30_000);
        sim.tick(root, SimulatedSensors.idle());
        advance(sim, 60_000);
        sim.tick(root, SimulatedSensors.idle());
        require(root.state() == RootState.STOPPED && action.starts() == 3,
            "final utility abandonment stops root rather than recovering forever");
        require(sim.ctx().utilityFailures().get("bot:lab", selector.children().getFirst().path(), "chest").consecutive() == 0,
            "bot stop clears session target abandonment");
        root.start();
        sim.tick(root, SimulatedSensors.idle());
        require(action.starts() == 4 && root.state() == RootState.RUNNING,
            "new bot session may retry previously abandoned target");
        root.stop(ExitReason.STOPPED);
    }
}
