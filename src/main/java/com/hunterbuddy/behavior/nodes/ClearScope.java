package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public final class ClearScope extends Node {
    private final Scope scope;

    public ClearScope(Scope scope) {
        super("clear(" + scope + ")");
        this.scope = Objects.requireNonNull(scope);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        ctx.bb().clear(ctx.owner(), scope);
        return Success.INSTANCE;
    }
}
