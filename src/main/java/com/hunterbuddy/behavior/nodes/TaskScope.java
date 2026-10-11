package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;

public final class TaskScope extends Decorator {
    public TaskScope(Node child) {
        super("task", child);
    }

    @Override
    protected void onStart(TickContext ctx) {
        String owner = ctx.owner();
        ctx.bb().openTask(owner);
        activation().defer(() -> ctx.bb().closeTask(owner));
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return child().execute(ctx);
    }
}
