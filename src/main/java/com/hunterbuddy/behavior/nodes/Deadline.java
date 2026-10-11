package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;

public final class Deadline extends Decorator {
    private final long limitMs;
    private long started;
    private long frozenAtStart;
    private boolean resume;

    public Deadline(long ms, Node child) {
        super("deadline(" + ms + ")", child);
        if (ms < 1) throw new IllegalArgumentException("Deadline must be positive");
        limitMs = ms;
    }

    @Override
    protected void onStart(TickContext ctx) {
        if (!resume) {
            started = ctx.nowMs();
            frozenAtStart = ctx.clock().frozenMs();
        }
        resume = false;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (ctx.nowMs() - started - (ctx.clock().frozenMs() - frozenAtStart) >= limitMs) {
            child().halt(ctx, ExitReason.ABORTED);
            return fail(FailureCode.DEADLINE, "limitMs=" + limitMs);
        }
        return child().execute(ctx);
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        resume = reason == ExitReason.PAUSED || reason == ExitReason.PREEMPTED;
    }

    @Override
    protected void onReset() {
        resume = false;
    }
}
