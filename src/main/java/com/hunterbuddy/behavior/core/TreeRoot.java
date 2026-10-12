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
    private long attemptBaselineMs;

    public TreeRoot(TickContext ctx, TreeHooks hooks, Recovery recovery) {
        this.ctx = Objects.requireNonNull(ctx);
        this.hooks = Objects.requireNonNull(hooks);
        this.recovery = Objects.requireNonNull(recovery);
        hooks.recovery(recovery);
        TreeValidator.validate(hooks.build(), recovery);
        TreeValidator.bind(recovery, "recovery", "core", Hook.RECOVERY);
        recovery.taskOwner(hooks.taskOwner());
        ctx.stopHandler(reason -> stop(ExitReason.STOPPED));
    }

    public void start() {
        if (state == RootState.RUNNING) return;
        mutate(() -> {
            if (state == RootState.RUNNING) return;
            recovery.reset();
            recovering = false;
            beginAttempt();
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
            hooks.haltAll(ctx, reason);
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
        try {
            ctx.begin(sensors);
            if (state == RootState.RUNNING) tickRunning(sensors);
            while (!pending.isEmpty()) pending.removeFirst().run();
            hooks.applyPendingChanges(ctx);
            ctx.end();
        } catch (RuntimeException ex) {
            fault(ex);
        } finally {
            ticking = false;
        }
    }

    private void fault(RuntimeException ex) {
        pending.clear();
        hooks.discardPendingChanges();
        ctx.event(hooks.build(), Kind.FAULT, FailureCode.FAULT, ex.getClass().getSimpleName(), Objects.toString(ex.getMessage(), ""));
        try {
            stopNow(ExitReason.FAULT);
        } catch (RuntimeException cleanup) {
            if (cleanup != ex) ex.addSuppressed(cleanup);
        } finally {
            pending.clear();
            hooks.discardPendingChanges();
            ctx.leases().releaseAll();
        }
        try {
            ctx.end();
        } catch (RuntimeException sinkFailure) {
            if (sinkFailure != ex) ex.addSuppressed(sinkFailure);
        }
    }

    private void tickRunning(Sensors sensors) {
        synchronizeTaskOwner();
        ctx.clock().countActive();
        ctx.monitor().sample(ctx.clock(), sensors);
        if (!recovering && ctx.monitor().fired()
            && ctx.monitor().inactiveMs() - attemptBaselineMs >= ctx.monitor().windowMs()) {
            ctx.event(hooks.build(), Kind.MONITOR, FailureCode.NO_PROGRESS, "fired", "inactiveMs=" + ctx.monitor().inactiveMs());
            enterRecovery();
        }
        Node active = recovering ? recovery : hooks.build();
        NodeResult result = active.execute(ctx);
        if (ctx.evidenceSequence() != evidenceSequence) {
            evidenceSequence = ctx.evidenceSequence();
            attemptBaselineMs = 0;
            completions.clear();
            if (ctx.monitor().sustainedFor(60_000)) recovery.provenProgress();
        }
        if (result instanceof Running) return;
        if (recovering) {
            completeRecovery(result);
            return;
        }
        if (result instanceof Success && hooks.build().successfulHook() == Hook.BOT) recovery.provenProgress();
        completions.addLast(ctx.tick());
        while (!completions.isEmpty() && ctx.tick() - completions.peekFirst() >= 20) completions.removeFirst();
        if (completions.size() >= 20 && ctx.monitor().inactiveMs() > 0) {
            ctx.event(active, Kind.MONITOR, FailureCode.LOOP, "root loop", "completions=20");
            enterRecovery();
        }
    }

    private void synchronizeTaskOwner() {
        String owner = hooks.taskOwner();
        if (recovery.taskOwner().equals(owner)) return;
        recovery.halt(ctx, ExitReason.STOPPED);
        recovery.reset();
        recovering = false;
        recovery.taskOwner(owner);
        beginAttempt();
    }

    private void completeRecovery(NodeResult result) {
        if (result instanceof Failure) {
            recovery.next(ctx);
            return;
        }
        if (recovery.level() == 4 && !recovery.restartReady()) return;
        recovering = false;
        beginAttempt();
        ctx.event(hooks.build(), Kind.RECOVERY, null, "task resume",
            "windowMs=" + ctx.monitor().windowMs() + " inactiveMs=" + ctx.monitor().inactiveMs());
        if (recovery.restartReady()) {
            recovery.reset();
        }
    }

    private void beginAttempt() {
        attemptBaselineMs = ctx.monitor().inactiveMs();
        completions.clear();
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
