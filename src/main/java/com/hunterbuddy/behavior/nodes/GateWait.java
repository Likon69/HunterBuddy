package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Objects;
import java.util.function.Predicate;

public final class GateWait extends Node {
    private final Predicate<TickContext> busy;
    private final long limitMs;
    private long started;
    private long frozen;

    public GateWait(String name, Predicate<TickContext> busy, long limitMs) {
        super(name);
        if (limitMs < 1) throw new IllegalArgumentException("invalid gate wait bound");
        this.busy = Objects.requireNonNull(busy);
        this.limitMs = limitMs;
    }

    @Override
    protected void onStart(TickContext ctx) {
        started = ctx.nowMs();
        frozen = ctx.clock().frozenMs();
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (!busy.test(ctx)) return Success.INSTANCE;
        if (ctx.nowMs() - started - (ctx.clock().frozenMs() - frozen) >= limitMs) {
            String reason = name() + " still active after " + limitMs / 1_000 + "s";
            ctx.event(this, Kind.GATE, null, "overdue", reason);
            ctx.stopBot(ExitReason.STOPPED, reason);
            return fail(FailureCode.TIMEOUT, reason);
        }
        return new Running(name() + " owns its resources");
    }
}
