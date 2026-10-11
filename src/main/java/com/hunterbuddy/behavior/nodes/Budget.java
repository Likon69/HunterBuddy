package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;
import java.util.function.Function;

public final class Budget extends Decorator {
    private final String budget;
    private final int max;
    private Function<TickContext, String> key = ctx -> "default";
    private boolean allowed;

    public Budget(String name, int max, Node child) {
        super("budget(" + name + ")", child);
        if (max < 1) throw new IllegalArgumentException("Budget must be positive");
        budget = Objects.requireNonNull(name);
        this.max = max;
    }

    public Budget keyedBy(Function<TickContext, String> key) {
        if (active()) throw new IllegalStateException("cannot configure an active Budget");
        this.key = Objects.requireNonNull(key);
        return this;
    }

    @Override
    protected void onStart(TickContext ctx) {
        allowed = ctx.budgets().spend(ctx.owner(), path(), budget, key.apply(ctx), max);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return allowed ? child().execute(ctx) : fail(FailureCode.BUDGET, budget + " limit=" + max);
    }
}
