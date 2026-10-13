package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Objects;
import java.util.function.Function;

public final class StopBot extends Node {
    private final ExitReason exitReason;
    private final Function<TickContext, String> reason;

    public StopBot(String reason) {
        this(ExitReason.STOPPED, reason);
    }

    public StopBot(ExitReason exitReason, String reason) {
        this(exitReason, fixedReason(reason));
    }

    private StopBot(ExitReason exitReason, Function<TickContext, String> reason) {
        super("stop-bot");
        this.exitReason = Objects.requireNonNull(exitReason);
        this.reason = Objects.requireNonNull(reason);
    }

    public static StopBot withReason(Function<TickContext, String> reason) {
        return new StopBot(ExitReason.STOPPED, reason);
    }

    public static StopBot withReason(ExitReason exitReason, Function<TickContext, String> reason) {
        return new StopBot(exitReason, reason);
    }

    private static Function<TickContext, String> fixedReason(String reason) {
        Objects.requireNonNull(reason);
        return ctx -> reason;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        String detail = Objects.requireNonNull(reason.apply(ctx));
        ctx.event(this, Kind.BOT, null, "stop", detail);
        ctx.stopBot(exitReason, detail);
        return Success.INSTANCE;
    }
}
