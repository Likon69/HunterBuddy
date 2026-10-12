package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.BehaviorEngine;
import com.hunterbuddy.behavior.core.BotCleanup;
import com.hunterbuddy.behavior.core.Clock;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.ProgressPolicy;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.core.TreeRoot;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.mc.BehaviorMessages;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trace.Kind;
import com.hunterbuddy.behavior.trace.LogSink;
import com.hunterbuddy.behavior.trace.RingSink;
import com.hunterbuddy.behavior.trace.Trace;
import com.hunterbuddy.behavior.trace.TraceEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ObservabilityChecks {
    private final List<String> checks = new ArrayList<>();

    private ObservabilityChecks() {
    }

    public static List<String> run() {
        ObservabilityChecks check = new ObservabilityChecks();
        check.cleanup();
        check.messages();
        check.safeIdle();
        check.logFiltering();
        check.fullLogging();
        return List.copyOf(check.checks);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("observability check failed: " + name);
        checks.add(name);
    }

    private HookEntry entry(Hook hook, String owner, Contributor contributor, boolean enabled, Node node) {
        return new HookEntry(hook, owner, contributor, 0,
            new Guard(new Condition(owner, sensors -> enabled), node), true, node);
    }

    private void cleanup() {
        Simulation sim = new Simulation();
        sim.ctx().begin(SimulatedSensors.idle());
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalArgumentException(message); });
        HookEntry core = entry(Hook.SESSION, "core", Contributor.CORE, false, new Succeed());
        HookEntry routine = entry(Hook.SURVIVAL, "routine:test", Contributor.ROUTINE, false, new Succeed());
        hooks.insert(core);
        hooks.insert(routine);
        hooks.applyPendingChanges(sim.ctx());
        Key<String> memory = new Key<>("cleanup-memory", String.class, Scope.OWNER, false);
        sim.ctx().bb().set("core", memory, "keep");
        sim.ctx().budgets().spend("core", "session", "attempt", "target", 1);
        BotCleanup.remove(sim.ctx(), hooks, null);
        BotCleanup.remove(sim.ctx(), hooks, "core");
        BotCleanup.remove(sim.ctx(), hooks, "routine:test");
        require(hooks.entries().equals(List.of(core, routine)), "absent bot or non-bot owner never removes core and routine entries");
        require(sim.ctx().bb().get("core", memory).orElseThrow().equals("keep")
            && !sim.ctx().budgets().spend("core", "session", "attempt", "target", 1),
            "failed installation cleanup preserves core memory and spent budget");
        Node task = new Timeout(100, new WithLease(Set.of(Resource.LOOK), LeasePolicy.WAIT, new SimulatedAction("cleanup")));
        hooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, true, task));
        hooks.applyPendingChanges(sim.ctx());
        sim.ctx().bb().set("bot:lab", memory, "remove");
        sim.ctx().budgets().spend("bot:lab", "task", "attempt", "target", 1);
        sim.tick(hooks.build());
        require(sim.ctx().leases().snapshot().size() == 1 && task.active(), "cleanup fixture holds an active bot lease");
        BotCleanup.remove(sim.ctx(), hooks, "bot:lab");
        require(hooks.entries().equals(List.of(core, routine)) && sim.ctx().leases().snapshot().isEmpty() && !task.active(),
            "bot cleanup halts its branch while retaining core and routine entries");
        require(sim.ctx().bb().get("bot:lab", memory).isEmpty()
            && sim.ctx().budgets().spend("bot:lab", "task", "attempt", "target", 1),
            "bot cleanup removes its own memory and budget");
        require(sim.ctx().bb().get("core", memory).orElseThrow().equals("keep")
            && !sim.ctx().budgets().spend("core", "session", "attempt", "target", 1),
            "bot cleanup leaves unrelated core memory and budgets intact");
        sim.ctx().end();
    }

    private void messages() {
        List<String> chat = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        boolean[] available = {false};
        BehaviorMessages messages = new BehaviorMessages(chat::add, logs::add, () -> available[0]);
        messages.alert("SafeIdle: resources released");
        messages.stopped(new StopReport(ExitReason.DEAD, "Player died", "bot:lab", 1, 50));
        messages.flush();
        require(chat.isEmpty() && logs.size() == 2, "alerts are logged and queued while chat is unavailable");
        available[0] = true;
        messages.flush();
        require(chat.equals(List.of("SafeIdle: resources released", "Stopped [DEAD]: Player died")),
            "chat availability flushes SafeIdle and precise stop messages in order");
        messages.flush();
        require(chat.size() == 2, "flushed alerts are never repeated");
        messages.alert("SafeIdle: resources released");
        require(chat.size() == 3 && logs.size() == 3, "available chat receives each new alert immediately");
        BehaviorMessages brokenLog = new BehaviorMessages(chat::add,
            message -> { throw new IllegalStateException("message log fault"); }, () -> true);
        try {
            brokenLog.alert("SafeIdle despite log fault");
            throw new IllegalStateException("message log fault accepted");
        } catch (IllegalStateException ex) {
            require(ex.getMessage().equals("message log fault") && chat.getLast().equals("SafeIdle despite log fault"),
                "log failure cannot prevent delivery to available chat");
        }
        boolean[] failed = {false};
        BehaviorMessages brokenChat = new BehaviorMessages(message -> {
            if (!failed[0]) {
                failed[0] = true;
                throw new IllegalStateException("chat fault");
            }
            chat.add(message);
        }, logs::add, () -> true);
        try {
            brokenChat.alert("retained alert");
            throw new IllegalStateException("chat fault accepted");
        } catch (IllegalStateException ex) {
            require(ex.getMessage().equals("chat fault"), "failed chat delivery exposes the adapter failure");
        }
        brokenChat.flush();
        require(chat.getLast().equals("retained alert"), "failed chat delivery retains pending alert for next flush");
    }

    private void safeIdle() {
        Simulation sim = new Simulation(new ProgressPolicy(Set.of(EvidenceKind.MILESTONE), 100));
        List<String> chat = new ArrayList<>();
        List<String> logs = new ArrayList<>();
        BehaviorMessages messages = new BehaviorMessages(chat::add, logs::add, () -> true);
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalArgumentException(message); });
        hooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, true, new Timeout(2_000, new SimulatedAction("stalled"))));
        hooks.applyPendingChanges(sim.ctx());
        Recovery recovery = new Recovery(new Fail(FailureCode.NO_OPTION, "none"), new Succeed(), messages::alert);
        TreeRoot root = new TreeRoot(sim.ctx(), hooks, recovery);
        root.start();
        for (int i = 0; i < 20 && chat.isEmpty(); i++) sim.tick(root, SimulatedSensors.idle());
        require(root.recoveryLevel() == 4 && chat.equals(List.of("SafeIdle: resources released")) && logs.size() == 1,
            "recovery level four announces SafeIdle through both chat and log");
        for (int i = 0; i < 10; i++) sim.tick(root, SimulatedSensors.idle());
        require(chat.size() == 1 && sim.ctx().leases().snapshot().isEmpty(), "SafeIdle wait does not flood chat or hold resources");
        for (int i = 0; i < 3_000 && root.state() != RootState.STOPPED; i++) sim.tick(root, SimulatedSensors.idle());
        require(root.state() == RootState.STOPPED && chat.size() == 2
            && chat.stream().allMatch(message -> message.equals("SafeIdle: resources released")),
            "restart exhaustion announces the two distinct idle periods without duplicate terminal stop alert");
        messages.stopped(root.stopReport().orElseThrow());
        require(chat.size() == 3 && chat.getLast().equals("Stopped [STOPPED]: automatic restart limit reached"),
            "terminal restart exhaustion is announced once through its precise stop report");
        sim.ctx().end();
    }

    private void logFiltering() {
        List<String> logs = new ArrayList<>();
        RingSink ring = new RingSink();
        Trace trace = new Trace(new LogSink(logs::add), ring);
        List<TraceEvent> noise = List.of(
            new TraceEvent(1, 50, Hook.BOT, "bot:lab", "root/task", Kind.START, null, "start", ""),
            new TraceEvent(2, 100, Hook.BOT, "bot:lab", "root/task", Kind.SUCCESS, null, "success", ""),
            new TraceEvent(2, 100, Hook.BOT, "bot:lab", "root", Kind.STOP, null, "COMPLETED", ""),
            new TraceEvent(3, 150, Hook.BOT, "bot:lab", "root/task", Kind.STOP, null, "STOPPED", ""),
            new TraceEvent(4, 200, Hook.BOT, "bot:lab", "root/task", Kind.FAILURE, FailureCode.TIMEOUT, "timeout", ""),
            new TraceEvent(5, 250, Hook.BOT, "bot:lab", "root/task", Kind.PROGRESS, null, "milestone", ""),
            new TraceEvent(6, 300, Hook.BOT, "bot:lab", "root/task", Kind.LEASE, null, "released", ""),
            new TraceEvent(40, 2_000, Hook.BOT, "bot:lab", "root/task", Kind.HEARTBEAT, null, "working", ""),
            new TraceEvent(40, 2_000, Hook.BOT, "core", "root", Kind.HOOK, null, "rebuilt", ""));
        noise.forEach(trace::emit);
        trace.publish();
        require(logs.isEmpty(), "normal node lifecycle lease progress and heartbeat never flood game log");
        require(ring.snapshot().equals(noise), "log filtering preserves complete detailed trace in RingSink");
        List<TraceEvent> important = List.of(
            new TraceEvent(41, 2_050, Hook.BOT, "core", "root", Kind.FAULT, FailureCode.FAULT, "injected", "fault details"),
            new TraceEvent(42, 2_100, Hook.BOT, "core", "root", Kind.STOP, null, "DEAD", "Player died"),
            new TraceEvent(43, 2_150, Hook.RECOVERY, "core", "recovery", Kind.RECOVERY, null, "safe idle", "leases=0"),
            new TraceEvent(44, 2_200, Hook.BOT, "core", "root", Kind.BOT, null, "switched", "lab"),
            new TraceEvent(45, 2_250, Hook.BOT, "core", "root", Kind.HOOK, null, "refused", "invalid plugin"),
            new TraceEvent(46, 2_300, Hook.BOT, "bot:lab", "root/task", Kind.FAILURE, FailureCode.REFUSED, "refused", "blocked action"),
            new TraceEvent(47, 2_350, Hook.BOT, "core", "root", Kind.MONITOR, FailureCode.NO_PROGRESS, "fired", "inactiveMs=180000"));
        important.forEach(trace::emit);
        trace.publish();
        require(logs.size() == important.size(), "important faults stops recoveries bot switches and refusals all reach log");
        require(logs.getFirst().contains("kind=FAULT") && logs.getFirst().contains("detail=fault details"),
            "filtered log keeps precise structured fault details");
        require(ring.snapshot().size() == noise.size() + important.size(), "important log events are also retained in detailed ring");
        logs.clear();
        long[] time = {0};
        BehaviorEngine engine = new BehaviorEngine(new Clock(() -> time[0]), report -> { }, message -> { }, new LogSink(logs::add));
        engine.startBot(new LabBot(LabScenario.WORK));
        for (int i = 0; i < 2_000; i++) {
            time[0] += 50;
            engine.tick();
        }
        require(logs.size() == 1 && logs.getFirst().contains("reason=started") && engine.snapshot().status().completed() == 50,
            "one hundred seconds of successful LabBot work log only session start");
        require(engine.snapshot().recent().stream().anyMatch(event -> event.kind() == Kind.STOP),
            "LabBot HUD still receives detailed node events suppressed from game log");
        engine.stopBot(ExitReason.STOPPED, "filter check finished");
        require(logs.size() == 1 && engine.snapshot().leases() == 0, "stop without active branch remains quiet and clean");
        engine.startBot(new LabBot(LabScenario.WORK));
        time[0] += 50;
        engine.tick();
        engine.stopBot(ExitReason.STOPPED, "active filter check finished");
        require(logs.size() == 3 && logs.getLast().contains("kind=STOP") && logs.getLast().contains("reason=STOPPED"),
            "explicit stop of active root logs one event without descendant spam");
    }

    private void fullLogging() {
        List<String> logs = new ArrayList<>();
        RingSink ring = new RingSink();
        boolean[] full = {false};
        long[] time = {0};
        BehaviorEngine engine = new BehaviorEngine(new Clock(() -> time[0]), report -> { }, message -> { },
            new LogSink(logs::add, () -> full[0]), ring);
        engine.startBot(new LabBot(LabScenario.WORK));
        time[0] += 50;
        engine.tick();
        require(logs.size() == 1 && logs.getFirst().contains("reason=started"),
            "disabled full trace setting preserves filtered game log");
        full[0] = true;
        logs.clear();
        int before = ring.snapshot().size();
        for (int i = 0; i < 40; i++) {
            time[0] += 50;
            engine.tick();
        }
        require(logs.size() == ring.snapshot().size() - before && !logs.isEmpty(),
            "enabled full trace logs every new LabBot event without restarting engine");
        require(logs.stream().anyMatch(line -> line.contains("kind=START"))
            && logs.stream().anyMatch(line -> line.contains("kind=SUCCESS"))
            && logs.stream().anyMatch(line -> line.contains("kind=PROGRESS"))
            && logs.stream().anyMatch(line -> line.contains("kind=LEASE")),
            "full laboratory log includes node lifecycle progress and lease diagnostics");
        require(engine.snapshot().status().completed() == 1 && engine.snapshot().evidence() == 1 && engine.snapshot().leases() == 1,
            "changing trace verbosity preserves simulated task progress and lease lifecycle");
        full[0] = false;
        logs.clear();
        for (int i = 0; i < 40; i++) {
            time[0] += 50;
            engine.tick();
        }
        require(logs.isEmpty() && ring.snapshot().size() > before,
            "disabling full trace restores filtering immediately while detailed ring continues");
        full[0] = true;
        before = ring.snapshot().size();
        engine.stopBot(ExitReason.STOPPED, "full trace check finished");
        require(logs.size() == ring.snapshot().size() - before && engine.snapshot().leases() == 0,
            "full trace includes complete stop cleanup events with no retained lease");
    }
}
