package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.List;
import java.util.Objects;

public abstract class Node {
    private final String name;
    private String path;
    private String owner = "core";
    private Hook hook = Hook.BOT;
    private ActivationScope scope;
    private boolean active;

    protected Node(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("empty node name");
        this.name = name;
        path = name;
    }

    public final NodeResult execute(TickContext ctx) {
        ctx.executed(hook);
        Node previous = ctx.enter(this);
        try {
            if (!active) {
                active = true;
                scope = new ActivationScope();
                ctx.event(this, Kind.START, null, "start", "");
                onStart(ctx);
            }
            NodeResult result = Objects.requireNonNull(onTick(ctx), "null node result: " + path);
            if (result instanceof Running) {
                if (ctx.tick() % 40 == 0) ctx.event(this, Kind.HEARTBEAT, null, ((Running) result).reason(), "");
            } else {
                if (result instanceof Failure failure) {
                    ctx.event(this, Kind.FAILURE, failure.code(), failure.reason(), "");
                    halt(ctx, ExitReason.FAILED);
                } else {
                    ctx.event(this, Kind.SUCCESS, null, "success", "");
                    halt(ctx, ExitReason.COMPLETED);
                }
            }
            return result;
        } catch (RuntimeException ex) {
            try {
                halt(ctx, ExitReason.FAULT);
            } catch (RuntimeException cleanup) {
                if (cleanup != ex) ex.addSuppressed(cleanup);
            }
            throw ex;
        } finally {
            ctx.leave(previous);
        }
    }

    public final void halt(TickContext ctx, ExitReason reason) {
        if (!active) return;
        active = false;
        Node previous = ctx.enter(this);
        RuntimeException failure = null;
        try {
            for (Node child : children()) {
                try {
                    child.halt(ctx, reason);
                } catch (RuntimeException ex) {
                    failure = aggregate(failure, ex);
                }
            }
            try {
                onStop(ctx, reason);
            } catch (RuntimeException ex) {
                failure = aggregate(failure, ex);
            }
            try {
                scope.close();
            } catch (RuntimeException ex) {
                failure = aggregate(failure, ex);
            }
            ctx.event(this, Kind.STOP, null, reason.name(), "");
        } finally {
            ctx.leave(previous);
        }
        if (failure != null) throw failure;
    }

    private RuntimeException aggregate(RuntimeException first, RuntimeException next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    protected void onStart(TickContext ctx) {
    }

    protected abstract NodeResult onTick(TickContext ctx);

    protected void onStop(TickContext ctx, ExitReason reason) {
    }

    protected void onReset() {
    }

    public final void resetMemory() {
        if (active) throw new IllegalStateException("cannot reset an active node");
        children().forEach(Node::resetMemory);
        onReset();
    }

    protected final ActivationScope activation() {
        return Objects.requireNonNull(scope, "node has not started");
    }

    protected final Failure fail(FailureCode code, String reason) {
        return new Failure(code, reason);
    }

    public List<Node> children() {
        return List.of();
    }

    public final void bind(String path, String owner, Hook hook) {
        if (active && this.path.equals(path) && this.owner.equals(owner) && this.hook == hook) return;
        if (active) throw new IllegalStateException("cannot bind an active node");
        this.path = Objects.requireNonNull(path);
        this.owner = Objects.requireNonNull(owner);
        this.hook = Objects.requireNonNull(hook);
    }

    public final boolean active() {
        return active;
    }

    public final String name() {
        return name;
    }

    public final String path() {
        return path;
    }

    public final String owner() {
        return owner;
    }

    public final Hook hook() {
        return hook;
    }
}
