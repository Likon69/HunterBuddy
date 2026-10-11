package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public final class Guard extends Decorator {
    private Condition enter;
    private Condition stay;
    private int minTicks;
    private long started;
    private int failureCooldown;
    private boolean flightAware = true;

    public Guard(Condition condition, Node child) {
        super("guard(" + condition.name() + ")", child);
        enter = Objects.requireNonNull(condition);
        stay = condition;
    }

    public Guard hold(int ticks) {
        requireInactive();
        if (ticks < 0) throw new IllegalArgumentException("negative guard hold");
        minTicks = ticks;
        return this;
    }

    public Guard enterWhen(Condition condition) {
        requireInactive();
        enter = Objects.requireNonNull(condition);
        return this;
    }

    public Guard stayWhile(Condition condition) {
        requireInactive();
        stay = Objects.requireNonNull(condition);
        return this;
    }

    public Guard onFailureCooldown(int ticks) {
        requireInactive();
        if (ticks < 0) throw new IllegalArgumentException("negative failure cooldown");
        failureCooldown = ticks;
        return this;
    }

    public void flightAware(boolean value) {
        requireInactive();
        flightAware = value;
    }

    private void requireInactive() {
        if (active()) throw new IllegalStateException("cannot configure an active Guard");
    }

    public boolean eligible(TickContext ctx) {
        if (!flightAware && ctx.sensors().gliding()) return false;
        if (active() && ctx.tick() - started < minTicks) return true;
        return (active() ? stay : enter).test(ctx.sensors());
    }

    public int failureCooldown() {
        return failureCooldown;
    }

    @Override
    protected void onStart(TickContext ctx) {
        started = ctx.tick();
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (!eligible(ctx)) {
            child().halt(ctx, ExitReason.GUARD_LOST);
            return fail(FailureCode.GUARD_LOST, name());
        }
        return child().execute(ctx);
    }
}
