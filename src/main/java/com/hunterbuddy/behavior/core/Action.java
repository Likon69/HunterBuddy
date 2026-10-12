package com.hunterbuddy.behavior.core;

public abstract class Action extends Node {
    private int limitTicks = 1_200;
    private long ticks;
    private boolean allowed;

    protected Action(String name) {
        super(name);
    }

    public final Action limit(int ticks) {
        if (ticks < 1) throw new IllegalArgumentException("Action limit must be positive");
        if (active()) throw new IllegalStateException("cannot change an active Action limit");
        limitTicks = ticks;
        return this;
    }

    @Override
    protected final void onStart(TickContext ctx) {
        ticks = 0;
        allowed = ctx.monitoring() || ctx.transitions().start(path(), target(ctx));
        if (allowed) onActionStart(ctx);
    }

    @Override
    protected final NodeResult onTick(TickContext ctx) {
        if (!allowed) return fail(FailureCode.LOOP, "action start budget exceeded");
        if (ticks++ >= limitTicks) return fail(FailureCode.TIMEOUT, "action limit=" + limitTicks);
        return onActionTick(ctx);
    }

    @Override
    protected final void onStop(TickContext ctx, ExitReason reason) {
        if (allowed) onActionStop(ctx, reason);
    }

    protected String target(TickContext ctx) {
        return "default";
    }

    protected void onActionStart(TickContext ctx) {
    }

    protected abstract NodeResult onActionTick(TickContext ctx);

    protected void onActionStop(TickContext ctx, ExitReason reason) {
    }
}
