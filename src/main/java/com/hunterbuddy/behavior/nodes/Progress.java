package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public final class Progress extends Node {
    private final String reason;

    public Progress(String reason) {
        super("progress");
        this.reason = Objects.requireNonNull(reason);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        ctx.progress(reason);
        return Success.INSTANCE;
    }
}
