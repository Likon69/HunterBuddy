package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

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
    private StopReport stopReport;

    public TreeRoot(TickContext ctx, TreeHooks hooks, Recovery recovery) {
        this.ctx = Objects.requireNonNull(ctx);
        this.hooks = Objects.requireNonNull(hooks);
        this.recovery = Objects.requireNonNull(recovery);
        hooks.recovery(recovery);
        TreeValidator.validate(hooks.build(), recovery);
        TreeValidator.bind(recovery, "recovery", "core", Hook.RECOVERY);
        recovery.taskOwner(hooks.taskOwner());
        ctx.stopReportHandler((reason, detail) -> stop(reason, detail));
    }

    public void start() {
        if (state == RootState.RUNNING) return;
        mutate(() -> {
            if (state == RootState.RUNNING) return;
            recovery.reset();
            recovering = false;
            beginAttempt();
            stopReport = null;
            state = RootState.RUNNING;
        });
    }

    public void stop(ExitReason reason) {
        stop(reason, Objects.requireNonNull(reason).name());
    }

    public void stop(ExitReason reason, String detail) {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(detail);
        mutate(() -> stopNow(reason, detail));
    }

    private void stopNow(ExitReason reason, String detail) {
        if (state == RootState.STOPPED && reason != ExitReason.FAULT) return;
        state = RootState.STOPPED;
        stopReport = new StopReport(reason, detail, hooks.taskOwner(), ctx.tick(), ctx.nowMs());
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
            ctx.utilityFailures().clear();
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

    void fault(RuntimeException ex) {
        pending.clear();
        hooks.discardPendingChanges();
        ctx.event(hooks.build(), Kind.FAULT, FailureCode.FAULT, ex.getClass().getSimpleName(), Objects.toString(ex.getMessage(), ""));
        try {
            stopNow(ExitReason.FAULT, ex.getClass().getSimpleName() + ": " + Objects.toString(ex.getMessage(), ""));
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
        if (deathReady()) {
            if (recovering) {
                recovery.halt(ctx, ExitReason.DEAD);
                recovering = false;
            }
            hooks.build().execute(ctx);
            return;
        }
        if (!recovering && ctx.monitor().fired() && noProgressRemainingMs() == 0) {
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

    private boolean deathReady() {
        return hooks.entries().stream().anyMatch(entry -> entry.hook() == Hook.DEATH && entry.guard().eligible(ctx));
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

    public Optional<StopReport> stopReport() {
        return Optional.ofNullable(stopReport);
    }

    public long noProgressRemainingMs() {
        if (state != RootState.RUNNING || recovering) return 0;
        long elapsed = Math.max(0, ctx.monitor().inactiveMs() - attemptBaselineMs);
        return Math.max(0, ctx.monitor().windowMs() - elapsed);
    }
}
