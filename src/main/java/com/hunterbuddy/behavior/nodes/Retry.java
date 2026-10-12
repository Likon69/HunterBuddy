package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.EnumSet;
import java.util.Set;

public final class Retry extends Decorator {
    private final int attempts;
    private final int backoffTicks;
    private Set<FailureCode> codes = EnumSet.complementOf(EnumSet.of(
        FailureCode.BUDGET, FailureCode.LOOP, FailureCode.PREEMPTED, FailureCode.FAULT));
    private int failures;
    private long waiting;
    private boolean resume;

    public Retry(int attempts, int backoffTicks, Node child) {
        super("retry(" + attempts + ")", child);
        if (attempts < 1 || backoffTicks < 0) throw new IllegalArgumentException("invalid retry limits");
        this.attempts = attempts;
        this.backoffTicks = backoffTicks;
    }

    public Retry on(FailureCode... codes) {
        if (active()) throw new IllegalStateException("cannot configure an active Retry");
        this.codes = Set.of(codes);
        return this;
    }

    @Override
    protected void onStart(TickContext ctx) {
        if (!resume) {
            failures = 0;
            waiting = 0;
        }
        resume = false;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (waiting > 0) {
            waiting--;
            return new Running("retry backoff remaining=" + waiting);
        }
        NodeResult result = child().execute(ctx);
        if (!(result instanceof Failure failure)) return result;
        failures++;
        if (!codes.contains(failure.code()) || failures >= attempts) return failure;
        child().resetMemory();
        waiting = (long) backoffTicks * failures;
        return new Running("retry attempt=" + (failures + 1) + "/" + attempts);
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        resume = reason == ExitReason.PREEMPTED || reason == ExitReason.PAUSED;
    }

    @Override
    protected void onReset() {
        failures = 0;
        waiting = 0;
        resume = false;
    }
}
