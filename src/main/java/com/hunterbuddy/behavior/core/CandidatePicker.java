package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.nodes.MarkFailed;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

public final class CandidatePicker<T> {
    public Optional<T> pick(TickContext ctx, String owner, String optionPath, List<T> candidates,
                            ToDoubleFunction<T> score, Function<T, String> id) {
        return inspect(ctx, owner, optionPath, candidates, score, id).candidate();
    }

    public CandidateSelection<T> inspect(TickContext ctx, String owner, String optionPath, List<T> candidates,
                                          ToDoubleFunction<T> score, Function<T, String> id) {
        return inspect(ctx, owner, optionPath, candidates, score, id, null);
    }

    public CandidateSelection<T> inspect(TickContext ctx, String owner, String optionPath, List<T> candidates,
                                          ToDoubleFunction<T> score, Function<T, String> id, String currentTarget) {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(optionPath);
        Objects.requireNonNull(candidates);
        Objects.requireNonNull(score);
        Objects.requireNonNull(id);
        T best = null;
        T current = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        long retryAt = Long.MAX_VALUE;
        for (T candidate : candidates) {
            String target = Objects.requireNonNull(id.apply(Objects.requireNonNull(candidate)), "null candidate id");
            if (ctx.bb().get(owner, MarkFailed.failedKey(target)).orElse(false)) continue;
            UtilityFailure failure = ctx.utilityFailures().get(owner, optionPath, target);
            if (failure.abandoned()) continue;
            double value = score.applyAsDouble(candidate);
            if (!Double.isFinite(value)) continue;
            if (failure.waiting(ctx.nowMs())) {
                retryAt = Math.min(retryAt, failure.retryAtMs());
                continue;
            }
            if (current == null && target.equals(currentTarget)) current = candidate;
            if (best == null || value > bestScore) {
                best = candidate;
                bestScore = value;
            }
        }
        T selected = current != null ? current : best;
        return new CandidateSelection<>(Optional.ofNullable(selected), selected == null && retryAt != Long.MAX_VALUE ? retryAt : 0);
    }
}
