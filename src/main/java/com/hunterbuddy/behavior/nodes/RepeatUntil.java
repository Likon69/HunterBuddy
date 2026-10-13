package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.function.BooleanSupplier;

public final class RepeatUntil extends Decorator {
    private final BooleanSupplier satisfied;
    private final int limit;
    private int repetitions;

    public RepeatUntil(String name, BooleanSupplier satisfied, int limit, Node child) {
        super(name, child);
        this.satisfied = satisfied;
        this.limit = limit;
    }

    @Override
    protected void onStart(TickContext ctx) {
        repetitions = 0;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (!child().active() && !child().suspended() && satisfied.getAsBoolean()) return Success.INSTANCE;
        if (repetitions >= limit) return fail(FailureCode.BUDGET, "bounded repetitions exhausted for " + name());
        NodeResult result = child().execute(ctx);
        if (!(result instanceof Success)) return result;
        repetitions++;
        child().resetMemory();
        return new Running("checking actual remaining work: " + name());
    }
}
