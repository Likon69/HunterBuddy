package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class TreeRoot {
    private final TickContext ctx;
    private final TreeHooks hooks;
    private final Recovery recovery;
    private final Deque<Runnable> pending = new ArrayDeque<>();
    private final Deque<Long> completions = new ArrayDeque<>();
    private RootState state = RootState.IDLE;
    private boolean ticking;
    private boolean recovering;
    private long evidenceSequence;

    public TreeRoot(TickContext ctx, TreeHooks hooks, Recovery recovery) {
        this.ctx = Objects.requireNonNull(ctx);
        this.hooks = Objects.requireNonNull(hooks);
        this.recovery = Objects.requireNonNull(recovery);
        hooks.recovery(recovery);
        TreeValidator.validate(hooks.build(), recovery);
        TreeValidator.bind(recovery, "recovery", "core", Hook.RECOVERY);
    }

    public void start() {
        mutate(() -> {
            if (state == RootState.RUNNING) return;
            recovery.reset();
            recovering = false;
            completions.clear();
            state = RootState.RUNNING;
        });
    }

    public void stop(ExitReason reason) {
        mutate(() -> stopNow(reason));
    }

    private void stopNow(ExitReason reason) {
        state = RootState.STOPPED;
        RuntimeException failure = null;
        try {
            hooks.build().halt(ctx, reason);
        } catch (RuntimeException ex) {
            failure = ex;
        }
        try {
            recovery.halt(ctx, reason);
        } catch (RuntimeException ex) {
            if (failure == null) failure = ex;
            else if (failure != ex) failure.addSuppressed(ex);
        } finally {
            ctx.leases().releaseAll();
            hooks.build().resetMemory();
            recovery.resetMemory();
        }
        if (failure != null) throw failure;
    }

    private void mutate(Runnable action) {
        if (ticking) pending.addLast(action);
        else action.run();
    }

    public void tick(Sensors sensors) {
        if (ticking) throw new IllegalStateException("reentrant root tick");
        ticking = true;
        ctx.begin(sensors);
        try {
            if (state != RootState.RUNNING) return;
            ctx.clock().countActive();
            ctx.monitor().sample(ctx.clock(), sensors);
            if (!recovering && ctx.monitor().fired()) {
                ctx.event(hooks.build(), Kind.MONITOR, FailureCode.NO_PROGRESS, "fired", "inactiveMs=" + ctx.monitor().inactiveMs());
                enterRecovery();
            }
            Node active = recovering ? recovery : hooks.build();
            NodeResult result = active.execute(ctx);
            if (ctx.evidenceSequence() != evidenceSequence) {
                evidenceSequence = ctx.evidenceSequence();
                completions.clear();
            }
            if (result instanceof Running) return;
            if (recovering) {
                if (result instanceof Failure) recovery.next(ctx);
                else recovering = false;
            } else {
                completions.addLast(ctx.tick());
                while (!completions.isEmpty() && ctx.tick() - completions.peekFirst() >= 20) completions.removeFirst();
                if (completions.size() >= 20 && ctx.monitor().inactiveMs() > 0) {
                    ctx.event(active, Kind.MONITOR, FailureCode.LOOP, "root loop", "completions=20");
                    enterRecovery();
                }
            }
        } catch (RuntimeException ex) {
            ctx.event(hooks.build(), Kind.FAULT, FailureCode.FAULT, ex.getClass().getSimpleName(), Objects.toString(ex.getMessage(), ""));
            try {
                stopNow(ExitReason.FAULT);
            } catch (RuntimeException cleanup) {
                if (cleanup != ex) ex.addSuppressed(cleanup);
            }
        } finally {
            try {
                while (!pending.isEmpty()) pending.removeFirst().run();
                hooks.applyPendingChanges(ctx);
            } catch (RuntimeException ex) {
                ctx.event(hooks.build(), Kind.FAULT, FailureCode.FAULT, "pending change failed", Objects.toString(ex.getMessage(), ""));
                stopNow(ExitReason.FAULT);
            } finally {
                ticking = false;
                ctx.end();
            }
        }
    }

    private void enterRecovery() {
        hooks.build().halt(ctx, ExitReason.ABORTED);
        recovery.next(ctx);
        recovering = true;
    }

    public RootState state() {
        return state;
    }

    public int recoveryLevel() {
        return recovery.level();
    }
}
