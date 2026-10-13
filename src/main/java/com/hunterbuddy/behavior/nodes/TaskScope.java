package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;

public final class TaskScope extends Decorator {
    private int depth;
    public TaskScope(Node child) {
        super("task", child);
    }

    @Override
    protected void onStart(TickContext ctx) {
        String owner = ctx.owner();
        ctx.bb().openTask(owner);
        depth = ctx.bb().taskDepth(owner);
        activation().defer(() -> { if (ctx.bb().taskDepth(owner) == depth) ctx.bb().closeTask(owner); });
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        while (ctx.bb().taskDepth(ctx.owner()) < depth) ctx.bb().openTask(ctx.owner());
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return child().execute(ctx);
    }
}
