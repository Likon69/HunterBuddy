package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;

public final class Timeout extends Decorator {
    private final int limit;
    private int ticks;
    private boolean resume;

    public Timeout(int ticks, Node child) {
        super("timeout(" + ticks + ")", child);
        if (ticks < 1) throw new IllegalArgumentException("Timeout must be positive");
        limit = ticks;
    }

    @Override
    protected void onStart(TickContext ctx) {
        if (!resume) ticks = 0;
        resume = false;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (ticks++ >= limit) {
            child().halt(ctx, ExitReason.ABORTED);
            return fail(FailureCode.TIMEOUT, "limit=" + limit);
        }
        return child().execute(ctx);
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        resume = reason == ExitReason.PAUSED || reason == ExitReason.PREEMPTED;
    }

    @Override
    protected void onReset() {
        ticks = 0;
        resume = false;
    }
}
