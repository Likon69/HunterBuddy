package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.TreeValidator;
import java.util.List;
import java.util.function.Supplier;

public final class Deferred extends Node {
    private final Supplier<Node> factory;
    private Node child;
    private boolean resumable;
    private boolean resume;

    public Deferred(String name, Supplier<Node> factory) {
        super(name);
        this.factory = factory;
    }

    public Deferred resumable() {
        if (active()) throw new IllegalStateException("cannot configure an active Deferred");
        resumable = true;
        return this;
    }

    @Override
    protected void onStart(TickContext ctx) {
        if (resume && child != null) {
            resume = false;
            return;
        }
        child = factory.get();
        TreeValidator.validate(child);
        TreeValidator.bind(child, path() + "/" + child.name(), ctx.owner(), hook());
    }

    @Override
    public List<Node> children() {
        return child == null ? List.of() : List.of(child);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return child.execute(ctx);
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        resume = resumable && (reason == ExitReason.PREEMPTED || reason == ExitReason.PAUSED);
    }

    @Override
    protected void onReset() {
        resume = false;
    }
}
