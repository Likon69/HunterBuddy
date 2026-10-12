package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.bot.Bot;
import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.bot.BotRegistry;
import com.hunterbuddy.behavior.bot.BotStatus;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.lab.LabBot;
import com.hunterbuddy.behavior.lab.SimulatedSensors;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.plugin.PluginRegistry;
import com.hunterbuddy.behavior.resource.ResourceArbiter;
import com.hunterbuddy.behavior.routine.DeathPolicy;
import com.hunterbuddy.behavior.routine.DeathRoutine;
import com.hunterbuddy.behavior.routine.IdleRoutine;
import com.hunterbuddy.behavior.routine.Routine;
import com.hunterbuddy.behavior.routine.RoutineContext;
import com.hunterbuddy.behavior.routine.SessionRoutine;
import com.hunterbuddy.behavior.trace.Kind;
import com.hunterbuddy.behavior.trace.RingSink;
import com.hunterbuddy.behavior.trace.Trace;
import com.hunterbuddy.behavior.trace.TraceEvent;
import com.hunterbuddy.behavior.trace.TraceSink;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class BehaviorEngine {
    private final Clock clock;
    private final TickContext ctx;
    private final TreeHooks hooks;
    private final Recovery recovery;
    private final TreeRoot root;
    private final List<Routine> routines;
    private final RoutineContext routineContext;
    private final PluginRegistry plugins;
    private final BotRegistry bots = new BotRegistry();
    private final RingSink ring = new RingSink();
    private final Consumer<StopReport> stopped;
    private final Deque<Runnable> pending = new ArrayDeque<>();
    private final Sensors sensors = SimulatedSensors.idle();
    private Bot bot;
    private BotContext botContext;
    private String botOwner;
    private StopReport announced;
    private boolean ticking;
    private long evidenceBaseline;
    private double tickMs;

    public BehaviorEngine(Clock clock, Consumer<StopReport> stopped, Consumer<String> alerts, TraceSink... sinks) {
        this.clock = Objects.requireNonNull(clock);
        this.stopped = Objects.requireNonNull(stopped);
        Objects.requireNonNull(alerts);
        RestartBudget restarts = new RestartBudget();
        routines = List.of(new SessionRoutine(), new DeathRoutine(), new IdleRoutine(alerts, restarts));
        List<TraceSink> allSinks = new ArrayList<>(List.of(sinks));
        allSinks.add(ring);
        ctx = new TickContext(clock, new Blackboard(), new ResourceArbiter(),
            new Trace(allSinks.toArray(TraceSink[]::new)), ProgressPolicy.DEFAULT);
        ctx.bb().world(sensors.dimensionKey());
        ctx.begin(sensors);
        hooks = new TreeHooks(message -> { throw new IllegalArgumentException(message); });
        plugins = new PluginRegistry(ctx, hooks, alerts);
        routineContext = new RoutineContext(ctx,
            () -> botContext == null ? DeathPolicy.STOP : botContext.config().deathPolicy());
        for (Routine routine : routines) routine.entries(routineContext).forEach(hooks::insert);
        hooks.applyPendingChanges(ctx);
        recovery = new Recovery(new Fail(FailureCode.NO_OPTION, "no simulated alternative"), new Succeed(), alerts, restarts);
        root = new TreeRoot(ctx, hooks, recovery);
    }

    public void startBot(Bot next) {
        checkBot(next);
        if (running()) throw new IllegalStateException("TreeJava is already running; use switch");
        if (ticking) throw new IllegalStateException("cannot start during an engine tick");
        try {
            install(next, "started");
        } catch (RuntimeException ex) {
            root.fault(ex);
            finishStop();
            throw ex;
        }
    }

    public void switchBot(Bot next) {
        checkBot(next);
        if (!running()) throw new IllegalStateException("TreeJava is not running");
        pending.addLast(() -> {
            stopNow(ExitReason.STOPPED, "Switching to " + next.name());
            install(next, "switched");
        });
    }

    private void checkBot(Bot next) {
        Objects.requireNonNull(next);
        if (!(next instanceof LabBot)) throw new IllegalArgumentException("Phase 1 supports simulated LabBot only");
    }

    private void install(Bot next, String reason) {
        BotContext context = new BotContext(ctx, "bot:" + next.name(), next.config());
        HookEntry entry = next.taskEntry(context);
        if (entry.hook() != Hook.BOT || entry.contributor() != Contributor.BOT
            || !entry.owner().equals("bot:" + next.name())) throw new IllegalArgumentException("invalid bot entry");
        TreeValidator.validate(List.of(entry));
        botOwner = entry.owner();
        hooks.insert(entry);
        hooks.applyPendingChanges(ctx);
        plugins.applyPendingCleanup();
        bot = next;
        botContext = context;
        ctx.monitor().configure(context.config().progress());
        evidenceBaseline = ctx.evidenceSequence();
        routines.forEach(routine -> routine.onInstall(routineContext));
        root.start();
        ctx.event(hooks.build(), Kind.BOT, null, reason, next.description());
        ctx.end();
    }

    public void stopBot(ExitReason reason, String detail) {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(detail);
        pending.clear();
        if (ticking) pending.addLast(() -> stopNow(reason, detail));
        else stopNow(reason, detail);
    }

    private void stopNow(ExitReason reason, String detail) {
        if (bot == null) return;
        try {
            root.stop(reason, detail);
            ctx.end();
        } catch (RuntimeException ex) {
            root.fault(ex);
        }
        finishStop();
    }

    public void tick() {
        tick(sensors);
    }

    public void tick(Sensors simulatedSensors) {
        Objects.requireNonNull(simulatedSensors);
        if (!running()) return;
        if (ticking) throw new IllegalStateException("reentrant engine tick");
        ticking = true;
        long started = System.nanoTime();
        try {
            try {
                clock.advance();
                ctx.begin(simulatedSensors);
                plugins.pulse();
                root.tick(simulatedSensors);
                plugins.applyPendingCleanup();
                if (running()) {
                    while (!pending.isEmpty()) pending.removeFirst().run();
                }
            } catch (RuntimeException ex) {
                pending.clear();
                root.fault(ex);
            }
            if (root.state() == RootState.STOPPED) {
                pending.clear();
                finishStop();
            }
        } finally {
            tickMs = (System.nanoTime() - started) / 1_000_000.0;
            ticking = false;
        }
    }

    private void finishStop() {
        StopReport report = root.stopReport().orElseThrow();
        if (announced == report) return;
        try {
            BotCleanup.remove(ctx, hooks, botOwner);
            plugins.applyPendingCleanup();
            botOwner = null;
            ctx.end();
        } catch (RuntimeException ex) {
            root.fault(ex);
            report = root.stopReport().orElseThrow();
        }
        for (Routine routine : routines) routine.onUninstall(report);
        announced = report;
        stopped.accept(report);
    }

    public EngineSnapshot snapshot() {
        Node tree = recovery.active() ? recovery : hooks.build();
        Node active = deepest(tree);
        int actionRemaining = remaining(tree);
        List<TraceEvent> events = ring.snapshot();
        return new EngineSnapshot(bot == null ? "none" : bot.name(), root.state(), active == null ? "none" : active.hook().name(),
            active == null ? "none" : active.path(), bot == null ? new BotStatus("none", "none", 0) : bot.status(),
            root.noProgressRemainingMs(), ctx.monitor().windowMs(), actionRemaining == Integer.MAX_VALUE ? 0 : actionRemaining, root.recoveryLevel(),
            ctx.leases().snapshot().size(), ctx.evidenceSequence() - evidenceBaseline, tickMs,
            events.subList(Math.max(0, events.size() - 5), events.size()), root.stopReport().orElse(null),
            routines.stream().map(Routine::status).toList());
    }

    public List<Routine> routines() {
        return routines;
    }

    public PluginRegistry plugins() {
        return plugins;
    }

    public BotRegistry bots() {
        return bots;
    }

    private boolean running() {
        return root.state() == RootState.RUNNING || root.state() == RootState.PAUSED;
    }

    private Node deepest(Node node) {
        if (!node.active()) return null;
        for (Node child : node.children()) {
            Node leaf = deepest(child);
            if (leaf != null) return leaf;
        }
        return node;
    }

    private int remaining(Node node) {
        if (!node.active()) return Integer.MAX_VALUE;
        int own = node instanceof Timeout timeout ? timeout.remainingTicks() : Integer.MAX_VALUE;
        for (Node child : node.children()) {
            if (child.active()) return Math.min(own, remaining(child));
        }
        return own;
    }
}
