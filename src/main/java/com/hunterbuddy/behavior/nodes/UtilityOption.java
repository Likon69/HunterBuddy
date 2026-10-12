package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

public record UtilityOption(Node node, ToDoubleFunction<TickContext> score, Function<TickContext, String> target) {
    public UtilityOption {
        Objects.requireNonNull(node);
        Objects.requireNonNull(score);
        Objects.requireNonNull(target);
    }
}
