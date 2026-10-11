package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Objects;
import java.util.function.Consumer;

public final class StopBot extends Node {
    private final String reason;
    private final Consumer<String> alert;

    public StopBot(String reason, Consumer<String> alert) {
        super("stop-bot");
        this.reason = Objects.requireNonNull(reason);
        this.alert = Objects.requireNonNull(alert);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        ctx.event(this, Kind.BOT, null, "stop", reason);
        ctx.stopBot(reason);
        alert.accept(reason);
        return Success.INSTANCE;
    }
}
