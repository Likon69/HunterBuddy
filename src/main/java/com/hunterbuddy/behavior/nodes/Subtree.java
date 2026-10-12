package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;
import java.util.function.Supplier;

public final class Subtree extends Decorator {
    public Subtree(String name, Supplier<? extends Node> factory) {
        super(name, Objects.requireNonNull(factory).get());
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return child().execute(ctx);
    }
}
