package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;
import java.util.function.Function;

public final class SetValue<T> extends Node {
    private final Key<T> key;
    private final Function<TickContext, T> value;

    public SetValue(Key<T> key, Function<TickContext, T> value) {
        super("set(" + key.name() + ")");
        this.key = Objects.requireNonNull(key);
        this.value = Objects.requireNonNull(value);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        ctx.bb().set(ctx.owner(), key, value.apply(ctx));
        return Success.INSTANCE;
    }
}
