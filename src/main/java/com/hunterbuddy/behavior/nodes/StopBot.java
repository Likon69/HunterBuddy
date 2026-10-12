package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Objects;

public final class StopBot extends Node {
    private final String reason;

    public StopBot(String reason) {
        super("stop-bot");
        this.reason = Objects.requireNonNull(reason);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        ctx.event(this, Kind.BOT, null, "stop", reason);
        ctx.stopBot(reason);
        return Success.INSTANCE;
    }
}
