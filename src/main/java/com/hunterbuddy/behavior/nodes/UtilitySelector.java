package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.CandidateSelection;
import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.UtilityFailure;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UtilitySelector extends Composite {
    private final List<UtilityOption> options;
    private final String[] previousTargets;
    private List<UtilityScore> scores = List.of();
    private int selected = -1;
    private String selectedTarget;
    private int heldTicks;
    private long lastHeldTick;

    public UtilitySelector(String name, UtilityOption... options) {
        super(name, nodes(options));
        this.options = List.of(options);
        previousTargets = new String[options.length];
    }

    private static Node[] nodes(UtilityOption[] options) {
        return List.of(options).stream().map(UtilityOption::node).toArray(Node[]::new);
    }

    @Override
    protected void onStart(TickContext ctx) {
        clearSelection();
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        Map<Integer, Set<String>> attempted = new HashMap<>();
        updateHold(ctx);
        for (int attempt = 0; attempt <= options.size(); attempt++) {
            score(ctx);
            int best = best(attempted);
            select(ctx, best);
            if (selected < 0) return unavailable(ctx);
            int executing = selected;
            if (previousTargets[executing] != null && !previousTargets[executing].equals(selectedTarget)) {
                children().get(executing).resetMemory();
            }
            previousTargets[executing] = selectedTarget;
            ctx.bb().set(ctx.owner(), TickContext.CURRENT_TARGET, selectedTarget);
            NodeResult result = children().get(executing).execute(ctx);
            if (result instanceof Success) {
                ctx.utilityFailures().succeeded(ctx.owner(), children().get(executing).path(), selectedTarget);
                previousTargets[executing] = null;
                return result;
            }
            if (!(result instanceof Failure)) return result;
            ctx.utilityFailures().failed(ctx.owner(), children().get(executing).path(), selectedTarget, ctx.nowMs());
            previousTargets[executing] = null;
            attempted.computeIfAbsent(executing, ignored -> new HashSet<>()).add(selectedTarget);
            clearSelection();
        }
        score(ctx);
        return unavailable(ctx);
    }

    private void updateHold(TickContext ctx) {
        if (selected >= 0 && ctx.tick() != lastHeldTick) {
            if (heldTicks < 100) heldTicks++;
            lastHeldTick = ctx.tick();
        }
    }

    private void score(TickContext ctx) {
        List<UtilityScore> next = new ArrayList<>(options.size());
        for (int i = 0; i < options.size(); i++) {
            UtilityOption option = options.get(i);
            double raw = option.score().applyAsDouble(ctx);
            if (!Double.isFinite(raw) || raw <= 0) {
                next.add(new UtilityScore(option.node().path(), "", raw, 0, Double.NEGATIVE_INFINITY, 0, false, false, false));
                continue;
            }
            CandidateSelection<String> selection = option.select(ctx, previousTargets[i]);
            String target = selection.candidate().orElse("");
            boolean hasTarget = selection.candidate().isPresent();
            UtilityFailure failure = hasTarget ? ctx.utilityFailures().get(ctx.owner(), option.node().path(), target) : UtilityFailure.NONE;
            boolean waiting = hasTarget ? failure.waiting(ctx.nowMs()) : selection.retryAtMs() > 0;
            double effective = Double.isFinite(raw) && hasTarget && !waiting && !failure.abandoned() ? raw : Double.NEGATIVE_INFINITY;
            next.add(new UtilityScore(option.node().path(), target, raw, failure.consecutive(), effective,
                hasTarget ? failure.retryAtMs() : selection.retryAtMs(), waiting, failure.abandoned(), selection.exhausted()));
        }
        scores = List.copyOf(next);
    }

    private int best(Map<Integer, Set<String>> attempted) {
        int best = -1;
        for (int i = 0; i < scores.size(); i++) {
            UtilityScore score = scores.get(i);
            if (!attempted.getOrDefault(i, Set.of()).contains(score.target()) && score.eligible()
                && (best < 0 || score.effective() > scores.get(best).effective())) best = i;
        }
        return best;
    }

    private void select(TickContext ctx, int best) {
        if (selected >= 0) {
            UtilityScore current = scores.get(selected);
            boolean targetChanged = current.wanted() && !current.target().equals(selectedTarget);
            if (!current.eligible() || targetChanged) {
                children().get(selected).halt(ctx, targetChanged ? ExitReason.ABORTED : ExitReason.GUARD_LOST);
                clearSelection();
            } else if (best != selected && best >= 0 && heldTicks >= 100
                && scores.get(best).effective() > current.effective() + 0.2) {
                children().get(selected).halt(ctx, ExitReason.PREEMPTED);
                clearSelection();
            }
        }
        if (selected < 0 && best >= 0) {
            selected = best;
            selectedTarget = scores.get(best).target();
            heldTicks = 0;
            lastHeldTick = ctx.tick();
        }
    }

    private NodeResult unavailable(TickContext ctx) {
        long retryAt = Long.MAX_VALUE;
        boolean abandoned = false;
        for (UtilityScore score : scores) {
            if (!score.wanted()) continue;
            if (score.eligible()) return new Running("utility selection deferred");
            if (score.waiting()) retryAt = Math.min(retryAt, score.retryAtMs());
            abandoned |= score.abandoned() || score.exhausted();
        }
        if (retryAt != Long.MAX_VALUE) return new Running("utility retry remainingMs=" + Math.max(0, retryAt - ctx.nowMs()));
        if (abandoned) {
            String reason = name() + ": no remaining target";
            ctx.event(this, Kind.BOT, FailureCode.NO_OPTION, "stop", reason);
            ctx.stopBot(reason);
        }
        return fail(FailureCode.NO_OPTION, name());
    }

    private void clearSelection() {
        selected = -1;
        selectedTarget = null;
        heldTicks = 0;
        lastHeldTick = 0;
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        clearSelection();
    }

    @Override
    protected void onReset() {
        clearSelection();
        scores = List.of();
        Arrays.fill(previousTargets, null);
    }

    public List<UtilityScore> scores() {
        return scores;
    }

    public String selectedPath() {
        return selected < 0 ? "" : children().get(selected).path();
    }
}
