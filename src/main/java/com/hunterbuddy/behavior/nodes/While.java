package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public final class While extends Decorator {
    private final Condition condition;

    public While(Condition condition, Node child) {
        super("while(" + condition.name() + ")", child);
        this.condition = Objects.requireNonNull(condition);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (!condition.test(ctx.sensors())) {
            child().halt(ctx, ExitReason.GUARD_LOST);
            return fail(FailureCode.GUARD_LOST, condition.name());
        }
        return child().execute(ctx);
    }
}
