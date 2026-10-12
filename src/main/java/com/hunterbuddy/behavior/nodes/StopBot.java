package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Objects;

public final class StopBot extends Node {
    private final ExitReason exitReason;
    private final String reason;

    public StopBot(String reason) {
        this(ExitReason.STOPPED, reason);
    }

    public StopBot(ExitReason exitReason, String reason) {
        super("stop-bot");
        this.exitReason = Objects.requireNonNull(exitReason);
        this.reason = Objects.requireNonNull(reason);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        ctx.event(this, Kind.BOT, null, "stop", reason);
        ctx.stopBot(exitReason, reason);
        return Success.INSTANCE;
    }
}
