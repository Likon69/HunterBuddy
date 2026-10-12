package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Objects;
import java.util.function.Consumer;

public final class SafeIdle extends Node {
    private final Consumer<String> alert;
    private long started;
    private long frozenAtStart;
    private long elapsedMs;

    public SafeIdle(Consumer<String> alert) {
        super("safe-idle");
        this.alert = Objects.requireNonNull(alert);
    }

    @Override
    protected void onStart(TickContext ctx) {
        started = ctx.nowMs();
        frozenAtStart = ctx.clock().frozenMs();
        ctx.leases().releaseAll();
        ctx.event(this, Kind.RECOVERY, null, "safe idle", "leases=0");
        alert.accept("SafeIdle: resources released");
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (elapsedMs + ctx.nowMs() - started - (ctx.clock().frozenMs() - frozenAtStart) >= 30_000) return Success.INSTANCE;
        return new Running("safe idle");
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        if (reason == ExitReason.PAUSED || reason == ExitReason.PREEMPTED) {
            elapsedMs += ctx.nowMs() - started - (ctx.clock().frozenMs() - frozenAtStart);
        } else elapsedMs = 0;
    }

    @Override
    protected void onReset() {
        elapsedMs = 0;
    }
}
