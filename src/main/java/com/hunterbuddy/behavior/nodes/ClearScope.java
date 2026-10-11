package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;
import java.util.function.Function;

public final class ClearScope extends Node {
    private final Scope scope;
    private final Function<TickContext, String> owner;

    public ClearScope(Scope scope) {
        this(scope, TickContext::owner);
    }

    public ClearScope(Scope scope, Function<TickContext, String> owner) {
        super("clear(" + scope + ")");
        this.scope = Objects.requireNonNull(scope);
        this.owner = Objects.requireNonNull(owner);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        ctx.bb().clear(owner.apply(ctx), scope);
        return Success.INSTANCE;
    }
}
