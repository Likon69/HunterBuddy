package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public final class WaitUntil extends Decorator {
    private final Condition condition;
    private final int limit;
    private int waiting;
    private boolean ready;
    private long startedMs;

    public WaitUntil(Condition condition, int timeoutTicks) {
        this(condition, timeoutTicks, new Succeed());
    }

    public WaitUntil(Condition condition, int timeoutTicks, Node child) {
        super("wait(" + condition.name() + ")", child);
        if (timeoutTicks < 1) throw new IllegalArgumentException("WaitUntil must be bounded");
        this.condition = Objects.requireNonNull(condition);
        limit = timeoutTicks;
    }

    @Override
    protected void onStart(TickContext ctx) {
        waiting = 0;
        ready = false;
        startedMs = ctx.nowMs();
    }

    public long elapsedMs(TickContext ctx) {
        return Math.max(0, ctx.nowMs() - startedMs);
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) { startedMs += ms; }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (!ready) {
            ready = condition.test(ctx.sensors());
            if (!ready) {
                if (++waiting >= limit) return fail(FailureCode.TIMEOUT, "waiting limit=" + limit);
                return new Running("waiting " + condition.name());
            }
        }
        return child().execute(ctx);
    }
}
