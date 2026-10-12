package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;

public final class Repeat extends Decorator {
    private final int count;
    private int completed;
    private boolean resume;

    public Repeat(int count, Node child) {
        super("repeat(" + count + ")", child);
        if (count < 0) throw new IllegalArgumentException("Repeat cannot be negative");
        this.count = count;
    }

    @Override
    protected void onStart(TickContext ctx) {
        if (!resume) completed = 0;
        resume = false;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (completed >= count) return Success.INSTANCE;
        NodeResult result = child().execute(ctx);
        if (!(result instanceof Success)) return result;
        completed++;
        if (completed >= count) return Success.INSTANCE;
        child().resetMemory();
        return new Running("repeat completed=" + completed + "/" + count);
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        resume = reason == ExitReason.PREEMPTED || reason == ExitReason.PAUSED;
    }

    @Override
    protected void onReset() {
        completed = 0;
        resume = false;
    }
}
