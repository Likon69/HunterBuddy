package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.CandidatePicker;
import com.hunterbuddy.behavior.core.CandidateSelection;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

public final class UtilityOption {
    private final Node node;
    private final ToDoubleFunction<TickContext> score;
    private final BiFunction<TickContext, String, CandidateSelection<String>> selection;

    public UtilityOption(Node node, ToDoubleFunction<TickContext> score, Function<TickContext, String> target) {
        this(fixed(target), node, score);
    }

    private UtilityOption(BiFunction<TickContext, String, CandidateSelection<String>> selection, Node node,
                          ToDoubleFunction<TickContext> score) {
        this.node = Objects.requireNonNull(node);
        this.score = Objects.requireNonNull(score);
        this.selection = Objects.requireNonNull(selection);
    }

    private static BiFunction<TickContext, String, CandidateSelection<String>> fixed(Function<TickContext, String> target) {
        Objects.requireNonNull(target);
        return (ctx, current) -> new CandidateSelection<>(Optional.of(Objects.requireNonNull(target.apply(ctx), "null utility target")), 0);
    }

    public static <T> UtilityOption withCandidates(Node node, ToDoubleFunction<TickContext> score,
                                                    Function<TickContext, List<T>> candidates,
                                                    ToDoubleFunction<T> candidateScore, Function<T, String> id) {
        Objects.requireNonNull(candidates);
        Objects.requireNonNull(candidateScore);
        Objects.requireNonNull(id);
        CandidatePicker<T> picker = new CandidatePicker<>();
        return new UtilityOption((ctx, current) -> picker.inspect(ctx, ctx.owner(), node.path(), candidates.apply(ctx),
            candidateScore, id, current).map(id), node, score);
    }

    public Node node() {
        return node;
    }

    public ToDoubleFunction<TickContext> score() {
        return score;
    }

    public CandidateSelection<String> select(TickContext ctx, String currentTarget) {
        return Objects.requireNonNull(selection.apply(ctx, currentTarget), "null candidate selection");
    }
}
