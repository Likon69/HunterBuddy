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
    private Stage stage = Stage.INACTIVE;
    private long suspendedTick;
    private long suspendedMs;
    private long suspendedFrozen;

    protected Node(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("empty node name");
        this.name = name;
        path = name;
    }

    public final NodeResult execute(TickContext ctx) {
        ctx.executed(hook);
        Node previous = ctx.enter(this);
        try {
            if (stage == Stage.INACTIVE) {
                stage = Stage.ACTIVE;
                scope = new ActivationScope();
                ctx.event(this, Kind.START, null, "start", "");
                onStart(ctx);
            } else if (stage == Stage.SUSPENDED) {
                stage = Stage.ACTIVE;
                onResume(ctx, ctx.tick() - suspendedTick, ctx.nowMs() - suspendedMs - (ctx.clock().frozenMs() - suspendedFrozen));
                ctx.event(this, Kind.GATE, null, "resumed", "activation retained");
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
        if (stage == Stage.INACTIVE) return;
        stage = Stage.INACTIVE;
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

    public final void suspend(TickContext ctx) {
        if (stage != Stage.ACTIVE) return;
        children().forEach(child -> child.suspend(ctx));
        stage = Stage.SUSPENDED;
        suspendedTick = ctx.tick();
        suspendedMs = ctx.nowMs();
        suspendedFrozen = ctx.clock().frozenMs();
        onSuspend(ctx);
        ctx.event(this, Kind.GATE, null, "suspended", "activation retained");
    }

    protected void onSuspend(TickContext ctx) {
    }

    protected void onResume(TickContext ctx, long ticks, long ms) {
    }

    public final boolean suspended() {
        return stage == Stage.SUSPENDED;
    }

    public final void post(TickContext ctx) {
        if (!active()) return;
        Node previous = ctx.enter(this);
        try {
            if (this instanceof PostTickable post) post.tickPost(ctx);
            for (Node child : children()) child.post(ctx);
        } finally { ctx.leave(previous); }
    }

    protected abstract NodeResult onTick(TickContext ctx);

    protected void onStop(TickContext ctx, ExitReason reason) {
    }

    protected void onReset() {
    }

    public final void resetMemory() {
        if (stage != Stage.INACTIVE) throw new IllegalStateException("cannot reset a running or suspended node");
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
        if (stage != Stage.INACTIVE && this.path.equals(path) && this.owner.equals(owner) && this.hook == hook) return;
        if (stage != Stage.INACTIVE) throw new IllegalStateException("cannot bind a running or suspended node");
        this.path = Objects.requireNonNull(path);
        this.owner = Objects.requireNonNull(owner);
        this.hook = Objects.requireNonNull(hook);
    }

    public final boolean active() {
        return stage == Stage.ACTIVE;
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

    private enum Stage {
        INACTIVE, ACTIVE, SUSPENDED
    }
}
