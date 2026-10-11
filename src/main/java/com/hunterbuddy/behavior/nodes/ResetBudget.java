package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;
import java.util.function.Function;

public final class ResetBudget extends Node {
    private final String budget;
    private final Function<TickContext, String> owner;

    public ResetBudget(String name) {
        this(name, TickContext::owner);
    }

    public ResetBudget(String name, Function<TickContext, String> owner) {
        super("reset-budget");
        budget = Objects.requireNonNull(name);
        this.owner = Objects.requireNonNull(owner);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        ctx.budgets().reset(owner.apply(ctx), budget);
        return Success.INSTANCE;
    }
}
