package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.bot.Bot;
import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.bot.BotRegistry;
import com.hunterbuddy.behavior.bot.BotStats;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import com.hunterbuddy.behavior.bot.BotStatus;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.bot.LiveWorldBot;
import com.hunterbuddy.behavior.lab.LabBot;
import com.hunterbuddy.behavior.lab.LiveLabBot;
import com.hunterbuddy.behavior.lab.SimulatedSensors;
import com.hunterbuddy.behavior.mc.ResumeGate;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.ReactiveSelector;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.UtilitySelector;
import com.hunterbuddy.behavior.persist.PersistentStore;
import com.hunterbuddy.behavior.plugin.PluginRegistry;
import com.hunterbuddy.behavior.resource.Leases;
import com.hunterbuddy.behavior.resource.LegacyGates;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.ResourceArbiter;
import com.hunterbuddy.behavior.routine.DeathPolicy;
import com.hunterbuddy.behavior.routine.DeathRoutine;
import com.hunterbuddy.behavior.routine.FlightKeeperRoutine;
import com.hunterbuddy.behavior.routine.IdleRoutine;
import com.hunterbuddy.behavior.routine.Routine;
import com.hunterbuddy.behavior.routine.RoutineContext;
import com.hunterbuddy.behavior.routine.RoutineStatus;
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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
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
    private final Map<Hook, Routine> selectedRoutines = new EnumMap<>(Hook.class);
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
    private PersistentStore store;
    private String worldKey = "simulated";
    private CompletableFuture<Map<Key<?>, Object>> memoryLoad;
    private CompletableFuture<Map<Key<?>, Object>> knowledgeLoad;
    private Map<Key<?>, Object> savedKnowledge = Map.of();
    private String savedKnowledgeWorld;
    private CompletableFuture<BotStats.Snapshot> statsLoad;
    private final SessionRoutine session;
    private long saveAt;
    private long loadingAt = -1;
    private final Map<String, CompletableFuture<BotStats.Snapshot>> statsReads = new LinkedHashMap<>();
    private final Set<String> statsRestored = new HashSet<>();
    private final AtomicLong checkpoint = new AtomicLong();
    private ConnectionPhase connectionPhase = ConnectionPhase.CONNECTED;

    public BehaviorEngine(Clock clock, Consumer<StopReport> stopped, Consumer<String> alerts, TraceSink... sinks) {
        this.clock = Objects.requireNonNull(clock);
        this.stopped = Objects.requireNonNull(stopped);
        this.alerts = Objects.requireNonNull(alerts);
        session = new SessionRoutine();
        routines = new ArrayList<>(List.of(session, new DeathRoutine(), new IdleRoutine(alerts, restarts)));
        List<TraceSink> allSinks = new ArrayList<>(List.of(sinks));
        allSinks.add(ring);
        Trace trace = new Trace(allSinks.toArray(TraceSink[]::new));
        ctx = new TickContext(clock, new Blackboard(), new ResourceArbiter(), trace, ProgressPolicy.DEFAULT);
        ctx.checkpointHandler(owner -> {
            if (owner.equals(botOwner)) persist();
            if (owner.equals(WorldKnowledge.OWNER)) persistKnowledge();
            if (store != null && owner.equals("bot:farmerbuddy") && !owner.equals(botOwner)) {
                checkpoint.incrementAndGet();
                store.save(worldKey, owner, ctx.bb().durable(owner));
            }
        });
        ctx.bb().world(sensors.dimensionKey());
        ctx.begin(sensors);
        hooks = new TreeHooks(message -> { throw new IllegalArgumentException(message); });
        plugins = new PluginRegistry(ctx, hooks, alerts);
        trace.add(plugins);
        routineContext = new RoutineContext(ctx,
            () -> botContext == null ? DeathPolicy.STOP : botContext.config().deathPolicy());
        for (Routine routine : routines) routine.entries(routineContext).forEach(hooks::insert);
        hooks.applyPendingChanges(ctx);
        recovery = new Recovery(new Fail(FailureCode.NO_OPTION, "no simulated alternative"), new Succeed(), alerts, restarts);
        root = new TreeRoot(ctx, hooks, recovery);
    }

    public void startBot(Bot next) {
        checkBot(next);
        if (running() || suspended()) throw new IllegalStateException("TreeJava is already running; use switch");
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
        switchBot(next, next instanceof LabBot ? "simulated" : worldKey);
    }

    public void switchBot(Bot next, String key) {
        checkBot(next);
        if (!running() && !suspended()) throw new IllegalStateException("TreeJava is not running");
        pending.addLast(() -> {
            stopNow(ExitReason.STOPPED, "Switching to " + next.name());
            if (root.stopReport().orElseThrow().reason() == ExitReason.FAULT) {
                pending.clear();
                return;
            }
            bindWorld(key);
            install(next, "switched");
        });
        if (suspended() && !ticking && callback == Callback.NONE) while (!pending.isEmpty()) pending.removeFirst().run();
    }

    private void checkBot(Bot next) {
        Objects.requireNonNull(next);
        if (!(next instanceof LiveLabBot) && !bots.names().contains(next.name())) {
            throw new IllegalArgumentException("Bot not registered: " + next.name());
        }
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
        if (store != null) {
            memoryLoad = store.load(worldKey, context.owner());
            knowledgeLoad = store.loadKnowledge(worldKey, WorldKnowledge.OWNER);
            statsLoad = statsRestored.contains(next.name()) ? null : statsReads.computeIfAbsent(next.name(), store::loadStats);
            loadingAt = -1;
        }
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
        if (node.active() || node.suspended()) throw new IllegalArgumentException("bot entry node active in another tree: " + node.name());
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
        if (!running() && !suspended()) return;
        if (ticking) throw new IllegalStateException("reentrant engine tick");
        ticking = true;
        long started = System.nanoTime();
        ctx.monitor().sampleValidity(sampleValidity);
        try {
            try {
                clock.advance();
                Sensors capturedSensors = Objects.requireNonNull(capture.get(), "sensor capture returned null");
                ctx.bb().world(store == null ? capturedSensors.dimensionKey() + "/" + capturedSensors.worldId() + "/" + capturedSensors.playerId() : worldKey);
                ctx.begin(capturedSensors);
                if (!loadMemory()) { root.sessionCause("loading persistent world"); ctx.end(); return; }
                if (suspended()) {
                    if (connectionPhase != ConnectionPhase.CONNECTED) { root.sessionCause("waiting for GameJoined"); ctx.end(); return; }
                    ResumeGate.Decision decision = session.resume(capturedSensors, clock.nowMs());
                    root.sessionCause(decision.cause());
                    if (decision.state() == ResumeGate.State.STOP || decision.state() == ResumeGate.State.DEAD) {
                        stopNow(decision.state() == ResumeGate.State.DEAD ? ExitReason.DEAD : ExitReason.STOPPED, decision.cause());
                    } else if (decision.state() == ResumeGate.State.READY) {
                        ctx.bb().restoreOwner(botOwner);
                        ctx.resetSeries();
                        callBot(Callback.RESUME, () -> bot.onResume(botContext));
                        root.resumeSession();
                        ctx.event(hooks.build(), Kind.BOT, null, "reconnected", "RESUME gate=ok");
                    }
                    ctx.end();
                    return;
                }
                plugins.pulse();
                long activeBefore = clock.activeMs();
                try {
                    root.tick(capturedSensors);
                } finally {
                    if (stats != null) {
                        stats.sampleTime(clock.activeMs() - activeBefore, ctx.executedHook());
                        if (root.state() == RootState.PAUSED) stats.samplePause(clock.activeGapMs(), ctx.executedHook() != null ? ctx.executedHook()
                            : ctx.gates().active().isEmpty() ? Hook.BOT_PRE : ctx.gates().active().getFirst().hook());
                    }
                }
                plugins.applyPendingCleanup();
                if (running()) {
                    while (!pending.isEmpty()) pending.removeFirst().run();
                }
                hooks.applyPendingChanges(ctx);
                plugins.applyPendingCleanup();
                ctx.end();
                ctx.outputs().flush(ctx.tick());
                if (store != null && clock.tick() - saveAt >= 40) persist();
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
        persist();
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
        NONE, START, STOP, RESUME
    }

    public void persistence(PersistentStore store, ResumeGate gate) {
        if (bot != null) throw new IllegalStateException("connect persistence before starting");
        this.store = Objects.requireNonNull(store);
        session.connect(gate);
    }

    public void bindWorld(String key) {
        if (running() && !worldKey.equals(key)) throw new IllegalStateException("world must be stopped before rebinding");
        worldKey = Objects.requireNonNull(key);
        ctx.bb().world(key);
    }

    public String worldKey() { return worldKey; }
    public boolean suspended() { return root.state() == RootState.SUSPENDED; }

    public Map<Key<?>, Object> durableMemory(String owner) {
        return ctx.bb().durable(owner);
    }

    public void disconnected() {
        if (suspended() || bot == null || !running()) return;
        if (!bot.config().resumeAfterReconnect()) { stopBot(ExitReason.DISCONNECTED, "Game session ended"); return; }
        pending.clear();
        persist();
        root.suspendSession();
        connectionPhase = ConnectionPhase.DISCONNECTED;
        ctx.bb().removeOwner(botOwner);
        session.worldLost();
        if (store != null) memoryLoad = store.load(worldKey, botOwner);
        ctx.event(hooks.build(), Kind.BOT, null, "suspended", "disconnected; all outputs released");
        ctx.end();
    }

    public void joined() { if (suspended()) { connectionPhase = ConnectionPhase.CONNECTED; session.worldLost(); } }

    private enum ConnectionPhase { CONNECTED, DISCONNECTED }

    public void tickPost() {
        if (!running()) return;
        try { root.tickPost(); ctx.end(); }
        catch (RuntimeException ex) { fault(ex); }
    }

    private boolean loadMemory() {
        if (memoryLoad != null && !memoryLoad.isDone() || statsLoad != null && !statsLoad.isDone()
            || knowledgeLoad != null && !knowledgeLoad.isDone()) {
            if (loadingAt < 0) loadingAt = clock.nowMs();
            if (clock.nowMs() - loadingAt >= 5_000) stopNow(ExitReason.STOPPED, "Persistent store loading exceeded 5 seconds");
            return false;
        }
        if (memoryLoad != null) {
            ctx.bb().loadDurable(botOwner, memoryLoad.exceptionally(ex -> Map.of()).join());
            memoryLoad = null;
        }
        if (knowledgeLoad != null) {
            ctx.bb().loadDurable(WorldKnowledge.OWNER, knowledgeLoad.exceptionally(ex -> Map.of()).join());
            knowledgeLoad = null;
        }
        if (statsLoad != null) {
            BotStats.Snapshot stored = statsLoad.exceptionally(ex -> BotStats.EMPTY).join();
            stats.mergeCumulative(stored.cumulative());
            statsRestored.add(bot.name());
            statsLoad = null;
        }
        return true;
    }

    private void persist() {
        if (store == null || bot == null || stats == null) return;
        persistKnowledge();
        String owner = "bot:" + bot.name();
        Map<Key<?>, Object> memory = new LinkedHashMap<>(ctx.bb().durable(owner));
        Map<Key<?>, Object> current = Map.copyOf(memory);
        BotStats.Snapshot snapshot = stats.snapshot();
        String key = worldKey;
        String name = bot.name();
        long sequence = checkpoint.incrementAndGet();
        CompletableFuture<Map<Key<?>, Object>> worldRead = memoryLoad == null ? CompletableFuture.completedFuture(Map.of()) : memoryLoad.exceptionally(ex -> Map.of());
        CompletableFuture<BotStats.Snapshot> totals = statsLoad == null ? CompletableFuture.completedFuture(BotStats.EMPTY) : statsLoad.exceptionally(ex -> BotStats.EMPTY);
        worldRead.thenCombine(totals, (loaded, previous) -> {
            if (checkpoint.get() != sequence) return null;
            Map<Key<?>, Object> merged = new LinkedHashMap<>(loaded);
            merged.putAll(current);
            BotStats.Snapshot combined = BotStats.combine(snapshot, previous.cumulative());
            merged.put(new Key<>("stats", BotStats.Snapshot.class, Scope.WORLD, true), combined);
            store.save(key, owner, merged);
            store.saveStats(name, combined);
            return null;
        });
        saveAt = clock.tick();
    }

    private void persistKnowledge() {
        if (store == null || knowledgeLoad != null) return;
        Map<Key<?>, Object> memory = ctx.bb().durable(WorldKnowledge.OWNER);
        if (memory.isEmpty() || worldKey.equals(savedKnowledgeWorld) && memory.equals(savedKnowledge)) return;
        store.save(worldKey, WorldKnowledge.OWNER, memory);
        savedKnowledge = memory;
        savedKnowledgeWorld = worldKey;
    }

    public EngineSnapshot snapshot() {
        Node tree = root.pausedNode() != null ? root.pausedNode() : recovery.active() ? recovery : hooks.build();
        Node active = deepest(tree);
        int actionRemaining = remaining(tree);
        List<TraceEvent> events = ring.snapshot();
        List<String> scoreLines = new ArrayList<>();
        scores(hooks.build(), scoreLines);
        return new EngineSnapshot(bot == null ? "none" : bot.name(), root.state(), active == null ? "none" : active.hook().name(),
            active == null ? "none" : active.path(), bot == null ? new BotStatus("none", "none", 0) : bot.status(),
            root.noProgressRemainingMs(), ctx.monitor().windowMs(), actionRemaining == Integer.MAX_VALUE ? 0 : actionRemaining, root.recoveryLevel(),
            ctx.leases().snapshot().size(), ctx.evidenceSequence() - evidenceBaseline, tickMs,
            events.subList(Math.max(0, events.size() - 5), events.size()), root.stopReport().orElse(null),
            routines.stream().map(this::routineState).toList(), stats == null ? BotStats.EMPTY : stats.snapshot(), root.pauseCause(),
            ctx.gates().status(), ctx.outputs().status(), scoreLines);
    }

    private RoutineStatus routineState(Routine routine) {
        if ((routine.slot() == Hook.SURVIVAL || routine.slot() == Hook.COMBAT || routine.slot() == Hook.REGEAR)
            && selectedRoutines.get(routine.slot()) != routine) return new RoutineStatus(routine.name(), routine.slot(), "off", "not selected");
        return routine.status();
    }

    public LegacyGates gates() {
        return ctx.gates();
    }

    public BotStats stats() {
        return stats;
    }

    public void resetStats() {
        statsByBot.values().forEach(BotStats::reset);
        statsRestored.addAll(statsByBot.keySet());
        statsLoad = null;
        persist();
    }

    public void registerRoutine(Routine routine) {
        if (bot != null) throw new IllegalStateException("register routines before starting");
        if (routines.stream().anyMatch(other -> other.name().equals(routine.name()))) throw new IllegalArgumentException("duplicate routine");
        routines.add(routine);
        selectedRoutines.put(routine.slot(), routine);
        routine.entries(routineContext).forEach(entry -> {
            entry.guard().availableWhen(() -> selectedRoutines.get(routine.slot()) == routine);
            hooks.insert(entry);
        });
        hooks.applyPendingChanges(ctx);
    }

    public void selectRoutine(Hook slot, String name) {
        if (slot != Hook.SURVIVAL && slot != Hook.COMBAT && slot != Hook.REGEAR) throw new IllegalArgumentException("routine slot must be survival, combat or regear");
        Routine next = name.equals("none") ? null : routines.stream().filter(routine -> routine.slot() == slot && routine.name().equals(name))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("unknown routine for " + slot + ": " + name));
        pending.addLast(() -> {
            Routine old = selectedRoutines.get(slot);
            if (old == next) return;
            if (old != null) old.entries(routineContext).forEach(entry -> hooks.remove(entry.owner()));
            if (next == null) selectedRoutines.remove(slot);
            else {
                selectedRoutines.put(slot, next);
                next.entries(routineContext).forEach(hooks::insert);
            }
        });
        if (!running() && !ticking) {
            while (!pending.isEmpty()) pending.removeFirst().run();
            hooks.applyPendingChanges(ctx);
            ctx.end();
        }
    }

    public List<String> routineStatus() {
        return routines.stream().map(routine -> routine.slot() + " " + routine.name() + " "
            + ((routine.slot() == Hook.SURVIVAL || routine.slot() == Hook.COMBAT || routine.slot() == Hook.REGEAR)
                && selectedRoutines.get(routine.slot()) != routine ? "off" : routine.status().state()) + " | " + routine.status().detail()).toList();
    }

    public List<String> why() {
        List<String> lines = new ArrayList<>();
        lines.add("Root=" + root.state() + " pause=" + root.pauseCause());
        root.stopReport().ifPresent(report -> lines.add("Stopped " + report.reason() + ": " + report.detail()));
        for (HookEntry entry : hooks.entries()) {
            lines.add(entry.hook() + " " + entry.owner() + " " + entry.guard().diagnostic(ctx));
            if (entry.guard().active()) break;
        }
        scores(hooks.build(), lines);
        lines.addAll(ctx.gates().status());
        ctx.leases().snapshot().forEach(lease -> lines.add("Lease " + lease.owner() + " " + lease.state() + " " + lease.resources()));
        return List.copyOf(lines);
    }

    private void scores(Node node, List<String> lines) {
        if (!(node.active() || node.suspended())) return;
        if (node instanceof UtilitySelector utility) utility.scores().forEach(score -> lines.add("Score " + score));
        node.children().forEach(child -> scores(child, lines));
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

    public LiveWorldBot liveBot() {
        return bot instanceof LiveWorldBot live ? live : null;
    }

    public String botName() {
        return bot == null ? "none" : bot.name();
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
