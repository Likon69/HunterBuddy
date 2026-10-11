package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Objects;
import java.util.function.Function;

public final class MarkFailed extends Node {
    private final Key<String> target;
    private final Function<TickContext, String> owner;

    public MarkFailed(Key<String> target) {
        this(target, TickContext::owner);
    }

    public MarkFailed(Key<String> target, Function<TickContext, String> owner) {
        super("mark-failed");
        this.target = Objects.requireNonNull(target);
        this.owner = Objects.requireNonNull(owner);
    }

    public static Key<Boolean> failedKey(String target) {
        return new Key<>("failed/" + target, Boolean.class, Scope.OWNER, true);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        String namespace = owner.apply(ctx);
        ctx.bb().get(namespace, target).ifPresent(value -> {
            ctx.bb().set(namespace, failedKey(value), true);
            ctx.event(this, Kind.RECOVERY, null, "target failed", value);
        });
        return Success.INSTANCE;
    }
}
