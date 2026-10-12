package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.bot.Bot;
import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.bot.BotRegistry;
import com.hunterbuddy.behavior.bot.BotStats;
import com.hunterbuddy.behavior.bot.BotStatus;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.lab.LabBot;
import com.hunterbuddy.behavior.lab.LiveLabBot;
import com.hunterbuddy.behavior.lab.SimulatedSensors;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.ReactiveSelector;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.plugin.PluginRegistry;
import com.hunterbuddy.behavior.resource.Leases;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.ResourceArbiter;
import com.hunterbuddy.behavior.routine.DeathPolicy;
import com.hunterbuddy.behavior.routine.DeathRoutine;
import com.hunterbuddy.behavior.routine.FlightKeeperRoutine;
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
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class BehaviorEngine {
    private final Clock clock;
    private final TickContext ctx;
    private final TreeHooks hooks;
    private Recovery recovery;
    private TreeRoot root;
    private final RestartBudget restarts = new RestartBudget();
    private final Consumer<String> alerts;
    private List<ReactiveSelector> recoveryGroups = List.of();
    private final List<Routine> routines;
    private final RoutineContext routineContext;
    private final PluginRegistry plugins;
    private final BotRegistry bots = new BotRegistry();
    private final Map<String, BotStats> statsByBot = new LinkedHashMap<>();
    private final RingSink ring = new RingSink();
    private final Consumer<StopReport> stopped;
    private final Deque<Runnable> pending = new ArrayDeque<>();
    private final Sensors sensors = SimulatedSensors.idle();
    private Bot bot;
    private Bot lifecycleBot;
    private Callback callback = Callback.NONE;
    private BotContext botContext;
    private BotStats stats;
    private String botOwner;
    private StopReport announced;
    private boolean ticking;
    private long evidenceBaseline;
    private double tickMs;

    public BehaviorEngine(Clock clock, Consumer<StopReport> stopped, Consumer<String> alerts, TraceSink... sinks) {
        this.clock = Objects.requireNonNull(clock);
        this.stopped = Objects.requireNonNull(stopped);
        this.alerts = Objects.requireNonNull(alerts);
        routines = new ArrayList<>(List.of(new SessionRoutine(), new DeathRoutine(), new IdleRoutine(alerts, restarts)));
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
        if (ticking || callback != Callback.NONE) throw new IllegalStateException("cannot start during an engine tick or bot callback");
        try {
            install(next, "started");
            while (running() && !pending.isEmpty()) pending.removeFirst().run();
        } catch (RuntimeException ex) {
            pending.clear();
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
            if (root.stopReport().orElseThrow().reason() == ExitReason.FAULT) {
                pending.clear();
                return;
            }
            install(next, "switched");
        });
    }

    private void checkBot(Bot next) {
        Objects.requireNonNull(next);
        if (!(next instanceof LabBot) && !(next instanceof LiveLabBot)) throw new IllegalArgumentException("Only LabBot scenarios are available");
    }

    private void install(Bot next, String reason) {
        stats = null;
        ctx.botStats(null);
        BotContext context = new BotContext(ctx, "bot:" + next.name(), next.config());
        List<HookEntry> entries = botEntries(next, context);
        List<HookEntry> mainEntries = entries.stream().filter(entry -> entry.hook() != Hook.RECOVERY).toList();
        List<ReactiveSelector> groups = new ArrayList<>();
        Recovery nextRecovery = new Recovery(
            recoveryOptions(2, entries, groups), recoveryOptions(3, entries, groups), alerts, restarts);
        List<HookEntry> candidate = new ArrayList<>(hooks.entries());
        candidate.addAll(mainEntries);
        TreeValidator.validate(candidate);
        TreeValidator.validate(ReactiveSelector.hooks(candidate.stream().map(HookEntry::guard).toArray(Node[]::new)), nextRecovery);
        botOwner = context.owner();
        recoveryGroups = List.copyOf(groups);
        hooks.recovery(nextRecovery);
        mainEntries.forEach(hooks::insert);
        hooks.applyPendingChanges(ctx);
        plugins.applyPendingCleanup();
        recovery = nextRecovery;
        root = new TreeRoot(ctx, hooks, recovery);
        bot = next;
        botContext = context;
        stats = statsByBot.computeIfAbsent(next.name(), name -> new BotStats());
        stats.startSession();
        ctx.botStats(stats);
        ctx.monitor().configure(context.config().progress());
        ctx.resetSeries();
        evidenceBaseline = ctx.evidenceSequence();
        routines.forEach(routine -> routine.onInstall(routineContext));
        lifecycleBot = next;
        callBot(Callback.START, () -> next.onStart(context));
        root.start();
        ctx.event(hooks.build(), Kind.BOT, null, reason, next.description());
        ctx.end();
    }

    private List<HookEntry> botEntries(Bot next, BotContext context) {
        HookEntry task = Objects.requireNonNull(next.taskEntry(context));
        if (task.hook() != Hook.BOT || task.contributor() != Contributor.BOT
            || !task.owner().equals(context.owner())) throw new IllegalArgumentException("invalid bot entry");
        List<HookEntry> entries = new ArrayList<>();
        entries.add(task);
        for (HookEntry entry : List.copyOf(next.extraEntries(context))) {
            if (entry.contributor() != Contributor.BOT || !entry.owner().equals(context.owner())) {
                throw new IllegalArgumentException("invalid bot extra entry owner or contributor");
            }
            if (entry.hook() != Hook.BOT_PRE && entry.hook() != Hook.BOT_POST && entry.hook() != Hook.RECOVERY) {
                throw new IllegalArgumentException("bot extra hook forbidden: " + entry.hook());
            }
            entries.add(entry);
        }
        TreeValidator.validateContributions(entries);
        entries.forEach(entry -> checkInactive(entry.guard()));
        return List.copyOf(entries);
    }

    private void checkInactive(Node node) {
        if (node.active()) throw new IllegalArgumentException("bot entry node active in another tree: " + node.name());
        node.children().forEach(this::checkInactive);
    }

    private Node recoveryOptions(int level, List<HookEntry> entries, List<ReactiveSelector> groups) {
        List<HookEntry> selected = entries.stream()
            .filter(entry -> entry.hook() == Hook.RECOVERY && entry.recoveryLevel() == level)
            .sorted(Comparator.comparingInt(HookEntry::priority).reversed()).toList();
        if (selected.isEmpty()) {
            return level == 2 ? new Fail(FailureCode.NO_OPTION, "no simulated alternative") : new Succeed();
        }
        selected.forEach(entry -> entry.guard().flightAware(entry.flightAware()));
        ReactiveSelector choices = new ReactiveSelector("bot-recovery-options-" + level,
            selected.stream().map(HookEntry::guard).toArray(Node[]::new));
        groups.add(choices);
        return choices;
    }

    public void stopBot(ExitReason reason, String detail) {
        Objects.requireNonNull(reason);
        Objects.requireNonNull(detail);
        if (callback == Callback.STOP) return;
        pending.clear();
        if (ticking || callback != Callback.NONE) pending.addLast(() -> stopNow(reason, detail));
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

    public void tick(Sensors capturedSensors) {
        Objects.requireNonNull(capturedSensors);
        tickCaptured(() -> capturedSensors);
    }

    public void tickCaptured(Supplier<Sensors> capture) {
        tickCaptured(capture, null);
    }

    public void tickCaptured(Supplier<Sensors> capture, BooleanSupplier sampleValidity) {
        Objects.requireNonNull(capture);
        if (!running()) return;
        if (ticking) throw new IllegalStateException("reentrant engine tick");
        ticking = true;
        long started = System.nanoTime();
        ctx.monitor().sampleValidity(sampleValidity);
        try {
            try {
                clock.advance();
                Sensors capturedSensors = Objects.requireNonNull(capture.get(), "sensor capture returned null");
                ctx.bb().world(capturedSensors.dimensionKey() + "/" + capturedSensors.worldId() + "/" + capturedSensors.playerId());
                ctx.begin(capturedSensors);
                plugins.pulse();
                long activeBefore = clock.activeMs();
                try {
                    root.tick(capturedSensors);
                } finally {
                    if (stats != null) stats.sampleTime(clock.activeMs() - activeBefore, ctx.executedHook());
                }
                plugins.applyPendingCleanup();
                if (running()) {
                    while (!pending.isEmpty()) pending.removeFirst().run();
                }
                ctx.outputs().flush(ctx.tick());
            } catch (RuntimeException ex) {
                pending.clear();
                root.fault(ex);
            }
            if (root.state() == RootState.STOPPED) {
                pending.clear();
                finishStop();
            }
        } finally {
            ctx.monitor().sampleValidity(null);
            tickMs = (System.nanoTime() - started) / 1_000_000.0;
            ticking = false;
        }
    }

    private void finishStop() {
        StopReport report = root.stopReport().orElseThrow();
        if (announced == report) return;
        Bot ending = lifecycleBot;
        lifecycleBot = null;
        if (ending != null) {
            StopReport callbackReport = report;
            try {
                callBot(Callback.STOP, () -> ending.onStop(callbackReport));
            } catch (RuntimeException ex) {
                root.fault(ex);
                report = root.stopReport().orElseThrow();
            }
        }
        try {
            for (ReactiveSelector group : recoveryGroups) group.detachForRebuild(Set.copyOf(group.children()));
            recoveryGroups = List.of();
            BotCleanup.remove(ctx, hooks, botOwner);
            plugins.applyPendingCleanup();
            botOwner = null;
            ctx.end();
        } catch (RuntimeException ex) {
            root.fault(ex);
            report = root.stopReport().orElseThrow();
        }
        for (Routine routine : routines) routine.onUninstall(report);
        if (stats != null) stats.stopped(report.reason());
        announced = report;
        stopped.accept(report);
    }

    private void callBot(Callback call, Runnable work) {
        if (callback != Callback.NONE) throw new IllegalStateException("reentrant bot callback");
        callback = call;
        ctx.stopReportHandler(this::stopBot);
        try {
            work.run();
        } finally {
            ctx.stopReportHandler((reason, detail) -> root.stop(reason, detail));
            callback = Callback.NONE;
        }
    }

    private enum Callback {
        NONE, START, STOP
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
            routines.stream().map(Routine::status).toList(), stats == null ? BotStats.EMPTY : stats.snapshot());
    }

    public List<Routine> routines() {
        return List.copyOf(routines);
    }

    public void flightKeeper(Node keepFlying) {
        if (bot != null || routines.stream().anyMatch(routine -> routine instanceof FlightKeeperRoutine)) {
            throw new IllegalStateException("FlightKeeper must be connected once before starting");
        }
        Routine routine = new FlightKeeperRoutine(keepFlying);
        routine.entries(routineContext).forEach(hooks::insert);
        hooks.applyPendingChanges(ctx);
        routines.add(routine);
    }

    public void advanceNativeOutputs() {
        ctx.outputs().advanceNative();
        ctx.end();
    }

    public PluginRegistry plugins() {
        return plugins;
    }

    public BotRegistry bots() {
        return bots;
    }

    public Outputs outputs() {
        return ctx.outputs();
    }

    public Leases leases() {
        return ctx.leases();
    }

    public LiveLabBot liveLab() {
        return bot instanceof LiveLabBot lab ? lab : null;
    }

    public void fault(RuntimeException failure) {
        Objects.requireNonNull(failure);
        pending.clear();
        root.fault(failure);
        finishStop();
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
