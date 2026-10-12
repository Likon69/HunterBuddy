package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.nodes.MarkFailed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.UtilityOption;
import com.hunterbuddy.behavior.nodes.UtilitySelector;
import java.util.ArrayList;
import java.util.List;

public final class TargetStabilityChecks {
    private final List<String> checks = new ArrayList<>();

    private TargetStabilityChecks() {
    }

    public static List<String> run() {
        TargetStabilityChecks check = new TargetStabilityChecks();
        check.stability();
        check.successReleasesTarget();
        check.unwanted();
        check.mixedOptions();
        return List.copyOf(check.checks);
    }

    private UtilitySelector prepare(UtilitySelector selector) {
        TreeValidator.validate(selector);
        TreeValidator.bind(selector, "target-stability", "bot:lab", Hook.BOT);
        return selector;
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("target stability check failed: " + name);
        checks.add(name);
    }

    private boolean noOption(Simulation sim, UtilitySelector selector) {
        return sim.tick(selector) instanceof Failure failure && failure.code() == FailureCode.NO_OPTION;
    }

    private void stability() {
        Simulation sim = new Simulation();
        List<String> candidates = new ArrayList<>(List.of("a", "b"));
        double[] ranking = {2, 1};
        List<String> worked = new ArrayList<>();
        SimulatedAction action = new SimulatedAction("approach", ctx -> {
            worked.add(ctx.bb().get(ctx.owner(), TickContext.CURRENT_TARGET).orElseThrow());
            return Running.WORKING;
        });
        Node node = new Timeout(500, action);
        UtilitySelector selector = prepare(new UtilitySelector("approach", UtilityOption.withCandidates(node,
            ctx -> 1, ctx -> List.copyOf(candidates), value -> ranking[value.equals("a") ? 0 : 1], value -> value)));
        sim.tick(selector);
        for (int i = 0; i < 120; i++) {
            ranking[0] = i % 2 == 0 ? 1 : 2;
            ranking[1] = i % 2 == 0 ? 2 : 1;
            sim.tick(selector);
        }
        require(action.starts() == 1 && action.stops().isEmpty() && worked.stream().allMatch("a"::equals),
            "valid running target survives alternating ranking even beyond minHold");
        selector.halt(sim.ctx(), ExitReason.PREEMPTED);
        ranking[0] = 1;
        ranking[1] = 2;
        sim.tick(selector);
        require(action.starts() == 2 && worked.getLast().equals("a"), "valid target resumes after external preemption despite new ranking");
        candidates.remove("a");
        sim.tick(selector);
        require(action.starts() == 3 && worked.getLast().equals("b")
            && action.stops().getLast() == ExitReason.ABORTED, "removed target replaced in same tick");
        candidates.add("a");
        sim.ctx().bb().set("bot:lab", MarkFailed.failedKey("b"), true);
        sim.tick(selector);
        require(action.starts() == 4 && worked.getLast().equals("a"), "MarkFailed invalidates preferred target");
        selector.halt(sim.ctx(), ExitReason.STOPPED);
        require(sim.ctx().evidenceSequence() == 0, "stable ranking creates no progress proof");
    }

    private void successReleasesTarget() {
        Simulation sim = new Simulation();
        double[] ranking = {2, 1};
        List<String> worked = new ArrayList<>();
        Node node = new Timeout(100, new SimulatedAction("finish", ctx -> {
            worked.add(ctx.bb().get(ctx.owner(), TickContext.CURRENT_TARGET).orElseThrow());
            return Success.INSTANCE;
        }));
        UtilitySelector selector = prepare(new UtilitySelector("finish", UtilityOption.withCandidates(node,
            ctx -> 1, ctx -> List.of("a", "b"), value -> ranking[value.equals("a") ? 0 : 1], value -> value)));
        sim.tick(selector);
        ranking[0] = 1;
        ranking[1] = 2;
        sim.tick(selector);
        require(worked.equals(List.of("a", "b")), "completed target is released for next selection");
    }

    private void unwanted() {
        Simulation sim = new Simulation();
        List<String> stops = new ArrayList<>();
        sim.ctx().stopHandler(stops::add);
        int[] scans = {0};
        SimulatedAction action = new SimulatedAction("unwanted");
        Node node = new Timeout(100, action);
        UtilitySelector selector = prepare(new UtilitySelector("unwanted", UtilityOption.withCandidates(node,
            ctx -> 0, ctx -> {
                scans[0]++;
                return List.<String>of();
            }, value -> 1, value -> value)));
        require(noOption(sim, selector) && stops.isEmpty() && action.starts() == 0 && scans[0] == 0,
            "unwanted empty option neither scans nor stops bot");
        sim.ctx().utilityFailures().failed("bot:lab", node.path(), "a", sim.ctx().nowMs());
        selector = prepare(new UtilitySelector("unwanted-wait", UtilityOption.withCandidates(node,
            ctx -> 0, ctx -> List.of("a"), value -> 1, value -> value)));
        require(noOption(sim, selector) && stops.isEmpty() && !selector.scores().getFirst().waiting(),
            "unwanted delayed option does not wait");
        for (int i = 0; i < 2; i++) sim.ctx().utilityFailures().failed("bot:lab", node.path(), "a", sim.ctx().nowMs());
        require(noOption(sim, selector) && stops.isEmpty(), "unwanted abandoned option does not stop bot");

        double[] desire = {1};
        selector = prepare(new UtilitySelector("desire-changes", UtilityOption.withCandidates(node,
            ctx -> desire[0], ctx -> List.of("b"), value -> 1, value -> value)));
        sim.tick(selector);
        desire[0] = 0;
        require(noOption(sim, selector) && action.stops().equals(List.of(ExitReason.GUARD_LOST)) && stops.isEmpty(),
            "task no longer desired halts work without waiting or stopping bot");
    }

    private void mixedOptions() {
        Simulation sim = new Simulation();
        List<String> stops = new ArrayList<>();
        sim.ctx().stopHandler(stops::add);
        Node waiting = new Timeout(100, new SimulatedAction("waiting"));
        Node unwanted = new Timeout(100, new SimulatedAction("unwanted"));
        UtilitySelector selector = prepare(new UtilitySelector("mixed",
            UtilityOption.withCandidates(waiting, ctx -> 1, ctx -> List.of("a"), value -> 1, value -> value),
            UtilityOption.withCandidates(unwanted, ctx -> 0, ctx -> List.<String>of(), value -> 1, value -> value)));
        sim.ctx().utilityFailures().failed("bot:lab", waiting.path(), "a", sim.ctx().nowMs());
        require(sim.tick(selector) instanceof Running && stops.isEmpty(), "wanted waiting option ignores unwanted exhaustion");
        selector.halt(sim.ctx(), ExitReason.STOPPED);

        selector = prepare(new UtilitySelector("wanted-exhausted",
            UtilityOption.withCandidates(waiting, ctx -> 1, ctx -> List.<String>of(), value -> 1, value -> value),
            UtilityOption.withCandidates(unwanted, ctx -> 0, ctx -> List.of("b"), value -> 1, value -> value)));
        sim.ctx().utilityFailures().failed("bot:lab", unwanted.path(), "b", sim.ctx().nowMs());
        require(noOption(sim, selector) && stops.size() == 1 && stops.getFirst().equals("wanted-exhausted: no remaining target"),
            "unwanted waiting option cannot delay stop of wanted exhausted task");
    }
}
