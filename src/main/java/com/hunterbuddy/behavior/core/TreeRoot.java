package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.nodes.GateWait;
import com.hunterbuddy.behavior.resource.GateOwner;
import com.hunterbuddy.behavior.resource.GatePolicy;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
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
    private HookEntry pauseEntry;
    private List<GateOwner> heldGates = List.of();
    private String pauseCause = "none";
    private long pauseStarted;
    private long pauseFrozen;
    private boolean pauseLimited;
    private boolean borrowed;

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
        if (state == RootState.RUNNING || state == RootState.PAUSED) return;
        mutate(() -> {
            if (state == RootState.RUNNING || state == RootState.PAUSED) return;
            recovery.reset();
            recovering = false;
            beginAttempt();
            stopReport = null;
            state = RootState.RUNNING;
        });
    }

    public void stop(ExitReason reason, String detail) {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(detail);
        mutate(() -> stopNow(reason, detail));
    }

    private void stopNow(ExitReason reason, String detail) {
        if (state == RootState.STOPPED && reason != ExitReason.FAULT) return;
        state = RootState.STOPPED;
        pauseEntry = null;
        pauseCause = "none";
        heldGates = List.of();
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
            ctx.endIdlePeriod();
            ctx.leases().releaseAll();
            try {
                ctx.utilityFailures().clear();
                hooks.build().resetMemory();
                recovery.resetMemory();
            } finally {
                ctx.outputs().release(ctx.tick());
            }
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
            if (state == RootState.RUNNING || state == RootState.PAUSED) tickRunning(sensors);
            while (!pending.isEmpty()) pending.removeFirst().run();
            hooks.applyPendingChanges(ctx);
            ctx.end();
        } catch (RuntimeException ex) {
            fault(ex);
        } finally {
            ticking = false;
        }
    }

    public void suspendSession() {
        if (state != RootState.RUNNING && state != RootState.PAUSED) return;
        pending.clear();
        hooks.haltAll(ctx, ExitReason.DISCONNECTED);
        recovery.halt(ctx, ExitReason.DISCONNECTED);
        ctx.leases().releaseAll();
        ctx.outputs().release(ctx.tick());
        hooks.build().resetMemory();
        recovery.resetMemory();
        recovering = false;
        pauseEntry = null;
        heldGates = List.of();
        pauseCause = "waiting for reconnection";
        state = RootState.SUSPENDED;
    }

    public void resumeSession() {
        if (state != RootState.SUSPENDED) throw new IllegalStateException("root is not suspended");
        recovery.reset();
        beginAttempt();
        pauseCause = "none";
        state = RootState.RUNNING;
    }

    public void sessionCause(String cause) { if (state == RootState.SUSPENDED) pauseCause = Objects.requireNonNull(cause); }

    public void tickPost() {
        if (state == RootState.RUNNING) { hooks.build().post(ctx); recovery.post(ctx); }
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
            try {
                ctx.outputs().release(ctx.tick());
            } catch (RuntimeException cleanup) {
                if (cleanup != ex) ex.addSuppressed(cleanup);
            }
        }
        try {
            ctx.end();
        } catch (RuntimeException sinkFailure) {
            if (sinkFailure != ex) ex.addSuppressed(sinkFailure);
        }
    }

    private void tickRunning(Sensors sensors) {
        synchronizeTaskOwner();
        ctx.gates().poll(sensors);
        HookEntry session = readyEntry(Hook.SESSION);
        if (session == null && hookReady(Hook.DEATH)) {
            if (pauseEntry != null) pauseEntry.guard().halt(ctx, ExitReason.DEAD);
            hooks.build().execute(ctx);
            return;
        }
        HookEntry gate = session == null ? hooks.entries().stream().filter(entry -> entry.node() instanceof GateWait)
            .filter(entry -> entry.guard().cooledDown(ctx.tick()) && entry.guard().eligible(ctx)).findFirst().orElse(null) : session;
        if (gate != null || !ctx.gates().active().isEmpty()) {
            pause(gate, sensors);
            return;
        }
        if (state == RootState.PAUSED && !resume()) return;
        ctx.clock().countActive();
        ctx.monitor().sample(ctx.clock(), sensors);
        if (!ctx.monitor().samplesValid(ctx.tick())) {
            ctx.event(hooks.build(), Kind.PROGRESS, null, "sample rejected", "rejected=setback");
        }
        if (hookReady(Hook.DEATH)) {
            if (recovering) {
                recovery.halt(ctx, ExitReason.DEAD);
                recovering = false;
            }
            hooks.build().execute(ctx);
            return;
        }
        if (hookReady(Hook.FLIGHT)) {
            if (recovering) recovery.halt(ctx, ExitReason.PAUSED);
            hooks.build().execute(ctx);
            return;
        }
        if (!recovering && ctx.monitor().fired() && noProgressRemainingMs() == 0) {
            ctx.event(hooks.build(), Kind.MONITOR, FailureCode.NO_PROGRESS, "fired", "inactiveMs=" + ctx.monitor().inactiveMs());
            enterRecovery();
        }
        if (recovering) hooks.build().halt(ctx, ExitReason.GUARD_LOST);
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
        if (result instanceof Failure failure && failure.code() == FailureCode.UNREACHABLE) {
            enterRecovery();
            return;
        }
        if (result instanceof Success && hooks.build().successfulHook() == Hook.IDLE) {
            recovery.reset();
            beginAttempt();
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

    private boolean hookReady(Hook hook) {
        return readyEntry(hook) != null;
    }

    private HookEntry readyEntry(Hook hook) {
        return hooks.entries().stream().filter(entry -> entry.hook() == hook
            && entry.guard().cooledDown(ctx.tick()) && entry.guard().eligible(ctx)).findFirst().orElse(null);
    }

    private void pause(HookEntry entry, Sensors sensors) {
        String cause = entry != null ? entry.owner() : ctx.gates().active().getFirst().name();
        if (state != RootState.PAUSED) {
            hooks.build().suspend(ctx);
            recovery.suspend(ctx);
            ctx.leases().park();
            ctx.outputs().suspend(ctx.gates().borrowedBaritone());
            pauseStarted = ctx.nowMs();
            pauseFrozen = ctx.clock().frozenMs();
            pauseLimited = false;
        }
        if (pauseEntry != entry && pauseEntry != null) pauseEntry.guard().halt(ctx, ExitReason.PREEMPTED);
        if (!heldGates.equals(ctx.gates().active()) || borrowed != ctx.gates().borrowedBaritone()) {
            ctx.leases().park();
            heldGates = ctx.gates().active();
            ctx.leases().gates(heldGates);
            ctx.outputs().suspend(ctx.gates().borrowedBaritone());
            borrowed = ctx.gates().borrowedBaritone();
            for (GateOwner owner : heldGates) ctx.event(hooks.build(), Kind.LEASE, null, "blocked", "by=" + owner.name());
        }
        if (!cause.equals(pauseCause)) ctx.event(hooks.build(), Kind.GATE, null, "paused", "cause=" + cause);
        pauseEntry = entry;
        pauseCause = cause;
        state = RootState.PAUSED;
        ctx.monitor().sample(ctx.clock(), sensors, false);
        if (entry != null && entry.guard().execute(ctx) instanceof Failure) entry.guard().failedAt(ctx.tick());
        limitPause();
    }

    private void limitPause() {
        if (!pauseLimited && ctx.nowMs() - pauseStarted - (ctx.clock().frozenMs() - pauseFrozen) >= GatePolicy.LEGACY.pauseLimitMs()) {
            pauseLimited = true;
            String detail = "Paused by " + pauseCause + " for " + GatePolicy.LEGACY.pauseLimitMs() / 60_000 + " min";
            ctx.event(hooks.build(), Kind.GATE, null, "overdue", detail);
            ctx.stopBot(ExitReason.STOPPED, detail);
        }
    }

    private boolean resume() {
        if (pauseEntry != null) pauseEntry.guard().halt(ctx, ExitReason.GUARD_LOST);
        pauseEntry = null;
        heldGates = List.of();
        ctx.leases().gates(heldGates);
        if (!ctx.leases().restore() || !ctx.outputs().resume()) {
            pauseCause = "waiting for legacy resource handoff";
            limitPause();
            return false;
        }
        state = RootState.RUNNING;
        ctx.event(hooks.build(), Kind.GATE, null, "resumed", "cause=" + pauseCause + " budgets retained");
        pauseCause = "none";
        return true;
    }

    public String pauseCause() {
        return pauseCause;
    }

    public Node pausedNode() {
        return pauseEntry == null ? null : pauseEntry.guard();
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
        hooks.build().resumeBotRetries();
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
