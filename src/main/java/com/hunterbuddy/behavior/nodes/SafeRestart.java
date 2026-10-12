package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.RestartBudget;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Objects;
import java.util.function.Consumer;

public final class SafeRestart extends Composite {
    private final RestartBudget restarts;
    private boolean restartReady;

    public SafeRestart(Consumer<String> alert, RestartBudget restarts) {
        super("safe-restart", new SafeIdle(alert), new StopBot("automatic restart limit reached"));
        this.restarts = Objects.requireNonNull(restarts);
    }

    @Override
    protected void onStart(TickContext ctx) {
        restartReady = false;
    }

    @Override
    protected void onReset() {
        restartReady = false;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = children().getFirst().execute(ctx);
        if (!(result instanceof Success)) return result;
        if (!restarts.tryRestart(ctx.nowMs())) return children().get(1).execute(ctx);
        restartReady = true;
        ctx.event(this, Kind.BOT, null, "restart", hook() == Hook.RECOVERY ? "level=4" : "source=idle");
        return result;
    }

    public boolean restartReady() {
        return restartReady;
    }
}
