package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public final class ResetBudget extends Node {
    private final String budget;

    public ResetBudget(String name) {
        super("reset-budget");
        budget = Objects.requireNonNull(name);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        ctx.budgets().reset(ctx.owner(), budget);
        return Success.INSTANCE;
    }
}
