package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;

public final class Selector extends Composite {
    private int index;
    private boolean propagateFailures;

    public Selector(String name, Node... children) {
        super(name, children);
    }

    public Selector propagateFailures() {
        if (active()) throw new IllegalStateException("cannot configure an active Selector");
        propagateFailures = true;
        return this;
    }

    @Override
    protected void onStart(TickContext ctx) {
        index = 0;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        Failure last = null;
        while (index < children().size()) {
            NodeResult result = children().get(index).execute(ctx);
            if (!(result instanceof Failure failure)) return result;
            last = failure;
            if (propagateFailures && failure.code() != FailureCode.CONDITION_FALSE
                && failure.code() != FailureCode.GUARD_LOST && failure.code() != FailureCode.NO_OPTION) {
                return failure;
            }
            index++;
        }
        return propagateFailures && last != null ? last : fail(FailureCode.NO_OPTION, name());
    }
}
