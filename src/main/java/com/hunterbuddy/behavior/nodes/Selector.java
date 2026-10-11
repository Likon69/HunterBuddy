package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;

public final class Selector extends Composite {
    private int index;

    public Selector(String name, Node... children) {
        super(name, children);
    }

    @Override
    protected void onStart(TickContext ctx) {
        index = 0;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        while (index < children().size()) {
            NodeResult result = children().get(index).execute(ctx);
            if (!(result instanceof Failure)) return result;
            index++;
        }
        return fail(FailureCode.NO_OPTION, name());
    }
}
