package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public final class If extends Decorator {
    private final Condition condition;
    private boolean admitted;

    public If(Condition condition, Node child) {
        super("if(" + condition.name() + ")", child);
        this.condition = Objects.requireNonNull(condition);
    }

    @Override
    protected void onStart(TickContext ctx) {
        admitted = condition.test(ctx.sensors());
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return admitted ? child().execute(ctx) : fail(FailureCode.CONDITION_FALSE, condition.name());
    }
}
