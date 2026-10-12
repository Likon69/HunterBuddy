package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;
import java.util.function.Function;

public final class SimulatedAction extends Action {
    private final Function<TickContext, NodeResult> work;

    public SimulatedAction(String name, Function<TickContext, NodeResult> work) {
        super(name);
        this.work = Objects.requireNonNull(work);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        return work.apply(ctx);
    }
}
