package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.trace.Kind;
import com.hunterbuddy.behavior.trace.TraceReasons;
import java.util.Objects;
import java.util.function.Predicate;

public final class GateWait extends Node {
    private final Predicate<TickContext> busy;
    private final long limitMs;
    private final boolean stopOnTimeout;
    private long started;
    private long frozen;
    private long stopped;
    private boolean retained;

    public GateWait(String name, Predicate<TickContext> busy, long limitMs) {
        this(name, busy, limitMs, true);
    }

    public GateWait(String name, Predicate<TickContext> busy, long limitMs, boolean stopOnTimeout) {
        super(name);
        if (limitMs < 1) throw new IllegalArgumentException("invalid gate wait bound");
        this.busy = Objects.requireNonNull(busy);
        this.limitMs = limitMs;
        this.stopOnTimeout = stopOnTimeout;
    }

    @Override
    protected void onStart(TickContext ctx) {
        if (!retained || ctx.nowMs() - stopped > limitMs) {
            started = ctx.nowMs();
            frozen = ctx.clock().frozenMs();
        }
        retained = false;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (!busy.test(ctx)) return Success.INSTANCE;
        if (ctx.nowMs() - started - (ctx.clock().frozenMs() - frozen) >= limitMs) {
            String reason = name() + " still active after " + limitMs / 1_000 + "s";
            ctx.event(this, Kind.GATE, null, TraceReasons.OVERDUE, reason);
            if (stopOnTimeout) ctx.stopBot(ExitReason.STOPPED, reason);
            return fail(FailureCode.TIMEOUT, reason);
        }
        return new Running(name() + " owns its resources");
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        retained = !stopOnTimeout && (reason == ExitReason.PREEMPTED || reason == ExitReason.PAUSED);
        stopped = ctx.nowMs();
    }

    @Override
    protected void onReset() {
        retained = false;
    }
}
