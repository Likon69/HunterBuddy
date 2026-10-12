package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;

public final class ReactiveSequence extends Composite {
    private final int prefix;
    private int index;

    public ReactiveSequence(String name, Node... children) {
        super(name, children);
        int count = 0;
        while (count < children().size() && children().get(count) instanceof Condition) count++;
        prefix = count;
    }

    @Override
    protected void onStart(TickContext ctx) {
        index = prefix;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        for (int i = 0; i < prefix; i++) {
            NodeResult result = children().get(i).execute(ctx);
            if (result instanceof Failure failure) {
                if (index < children().size()) children().get(index).halt(ctx, ExitReason.GUARD_LOST);
                return fail(FailureCode.GUARD_LOST, name() + ": " + failure.reason());
            }
        }
        while (index < children().size()) {
            NodeResult result = children().get(index).execute(ctx);
            if (result instanceof Running) return result;
            if (result instanceof Failure failure) return fail(failure.code(), name() + ": " + failure.reason());
            index++;
        }
        return Success.INSTANCE;
    }

    @Override
    protected void onReset() {
        index = prefix;
    }
}
