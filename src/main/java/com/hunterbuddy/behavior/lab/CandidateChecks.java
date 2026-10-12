package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.CandidatePicker;
import com.hunterbuddy.behavior.core.CandidateSelection;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
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

public final class CandidateChecks {
    private final CandidatePicker<String> picker = new CandidatePicker<>();
    private final List<String> checks = new ArrayList<>();

    private CandidateChecks() {
    }

    public static List<String> run() {
        CandidateChecks check = new CandidateChecks();
        check.ranking();
        check.failures();
        check.delays();
        check.isolation();
        check.utilityIntegration();
        return List.copyOf(check.checks);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("candidate check failed: " + name);
        checks.add(name);
    }

    private CandidateSelection<String> inspect(Simulation sim, List<String> candidates) {
        return picker.inspect(sim.ctx(), "bot:lab", "mine", candidates, value -> value.equals("a") ? 2 : 1, value -> value);
    }

    private void ranking() {
        Simulation sim = new Simulation();
        require(inspect(sim, List.of("b", "a")).candidate().orElseThrow().equals("a"),
            "CandidatePicker selects greatest finite score");
        require(picker.pick(sim.ctx(), "bot:lab", "mine", List.of("a", "b"), value -> -1, value -> value)
            .orElseThrow().equals("a"), "CandidatePicker supports negative scores and stable ties");
        require(picker.pick(sim.ctx(), "bot:lab", "mine", List.of("a", "b"),
            value -> value.equals("a") ? Double.NaN : 0, value -> value).orElseThrow().equals("b"),
            "CandidatePicker rejects nonfinite score and accepts zero");
        require(inspect(sim, List.of()).exhausted(), "empty CandidatePicker reports exhaustion");
        require(sim.ctx().evidenceSequence() == 0, "candidate selection is not progress evidence");
    }

    private void failures() {
        Simulation sim = new Simulation();
        sim.ctx().bb().set("bot:lab", MarkFailed.failedKey("a"), true);
        require(inspect(sim, List.of("a", "b")).candidate().orElseThrow().equals("b"),
            "CandidatePicker skips MarkFailed target");
        require(inspect(sim, List.of("a")).exhausted(), "all MarkFailed candidates report exhaustion");
        sim.ctx().bb().remove("bot:lab", MarkFailed.failedKey("a"));
        for (int i = 0; i < 3; i++) sim.ctx().utilityFailures().failed("bot:lab", "mine", "a", sim.ctx().nowMs());
        require(inspect(sim, List.of("a", "b")).candidate().orElseThrow().equals("b"),
            "CandidatePicker skips abandoned target and selects alternative");
        require(inspect(sim, List.of("a")).exhausted(), "all abandoned candidates report exhaustion");
        sim.ctx().utilityFailures().clear();
        require(inspect(sim, List.of("a")).candidate().isPresent(), "CandidatePicker abandonment clears with session history");
    }

    private void delays() {
        Simulation sim = new Simulation();
        sim.ctx().utilityFailures().failed("bot:lab", "mine", "a", sim.ctx().nowMs());
        require(inspect(sim, List.of("a", "b")).candidate().orElseThrow().equals("b"),
            "CandidatePicker tries alternative instead of waiting on failed block");
        CandidateSelection<String> waiting = inspect(sim, List.of("a"));
        require(waiting.candidate().isEmpty() && !waiting.exhausted() && waiting.retryAtMs() == 30_000,
            "CandidatePicker distinguishes temporary delay from exhausted targets");
        sim.advance(29_999);
        require(inspect(sim, List.of("a")).candidate().isEmpty(), "CandidatePicker respects full first retry delay");
        sim.advance(1);
        require(inspect(sim, List.of("a")).candidate().isPresent(), "CandidatePicker permits target at exact expiration");
        require(sim.ctx().utilityFailures().get("bot:lab", "mine", "a").consecutive() == 1,
            "CandidatePicker never resets consecutive failures on selection");
        sim.ctx().utilityFailures().failed("bot:lab", "mine", "a", sim.ctx().nowMs());
        sim.ctx().utilityFailures().failed("bot:lab", "mine", "b", sim.ctx().nowMs());
        waiting = inspect(sim, List.of("a", "b"));
        require(waiting.retryAtMs() == 60_000 && !waiting.exhausted(), "CandidatePicker exposes earliest retry among delayed targets");
        sim.ctx().bb().set("bot:lab", MarkFailed.failedKey("b"), true);
        require(inspect(sim, List.of("a", "b")).retryAtMs() == 90_000,
            "MarkFailed exclusion takes precedence over temporary delay");
    }

    private void isolation() {
        Simulation sim = new Simulation();
        sim.ctx().utilityFailures().failed("bot:lab", "mine", "a", sim.ctx().nowMs());
        require(picker.pick(sim.ctx(), "bot:other", "mine", List.of("a"), value -> 1, value -> value).isPresent(),
            "candidate utility history isolated by owner");
        require(picker.pick(sim.ctx(), "bot:lab", "deposit", List.of("a"), value -> 1, value -> value).isPresent(),
            "candidate utility history isolated by task path");
        sim.ctx().bb().world("world:a");
        sim.ctx().bb().set("bot:lab", MarkFailed.failedKey("b"), true);
        sim.ctx().bb().removeOwner("bot:lab");
        sim.ctx().bb().restoreOwner("bot:lab");
        require(inspect(sim, List.of("b")).exhausted(), "CandidatePicker honors durable restored MarkFailed memory");
        sim.ctx().bb().removeOwner("bot:lab");
        sim.ctx().bb().world("world:b");
        sim.ctx().bb().restoreOwner("bot:lab");
        require(inspect(sim, List.of("b")).candidate().isPresent(), "CandidatePicker respects restored world namespace");
    }

    private void utilityIntegration() {
        Simulation sim = new Simulation();
        List<String> stops = new ArrayList<>();
        List<String> worked = new ArrayList<>();
        sim.ctx().stopHandler(stops::add);
        String[] selected = {""};
        Node work = new Timeout(100, new SimulatedAction("mine", ctx -> {
            selected[0] = ctx.bb().get(ctx.owner(), TickContext.CURRENT_TARGET).orElseThrow();
            worked.add(selected[0]);
            return selected[0].equals("a") ? new Failure(FailureCode.NOT_CONFIRMED, "a failed") : Success.INSTANCE;
        }));
        UtilitySelector selector = new UtilitySelector("mine-task", UtilityOption.withCandidates(work,
            ctx -> 1, ctx -> List.of("a", "b"), value -> value.equals("a") ? 2 : 1, value -> value));
        TreeValidator.validate(selector);
        TreeValidator.bind(selector, "selection", "bot:lab", Hook.BOT);
        require(sim.tick(selector) instanceof Success && worked.equals(List.of("a", "b")) && stops.isEmpty(),
            "UtilitySelector with CandidatePicker moves to another block in same tick after failure");
        for (int i = 0; i < 2; i++) sim.ctx().utilityFailures().failed("bot:lab", work.path(), "a", sim.ctx().nowMs());
        require(sim.tick(selector) instanceof Success && selected[0].equals("b") && stops.isEmpty(),
            "abandoned block never stops utility task while alternative remains");
    }
}
