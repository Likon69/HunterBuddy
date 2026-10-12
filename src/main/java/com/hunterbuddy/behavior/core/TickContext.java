package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.bot.BotStats;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.resource.Leases;
import com.hunterbuddy.behavior.trace.Kind;
import com.hunterbuddy.behavior.trace.Trace;
import com.hunterbuddy.behavior.trace.TraceEvent;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TickContext {
    public static final Key<String> CURRENT_TARGET = new Key<>("current-target", String.class, Scope.OWNER, false);
    private final Clock clock;
    private final Blackboard blackboard;
    private final Leases leases;
    private final Trace trace;
    private final ProgressMonitor monitor;
    private final UtilityFailures utilityFailures;
    private final TransitionLedger transitions = new TransitionLedger();
    private final BudgetLedger budgets = new BudgetLedger();
    private Sensors sensors;
    private Node current;
    private long evidenceSequence;
    private BiConsumer<ExitReason, String> stopHandler;
    private int monitorDepth;
    private boolean idleAnnounced;
    private String pluginOwner;
    private BotStats botStats;
    private Hook executedHook;

    public TickContext(Clock clock, Blackboard blackboard, Leases leases, Trace trace, ProgressPolicy policy) {
        this.clock = Objects.requireNonNull(clock);
        this.blackboard = Objects.requireNonNull(blackboard);
        this.leases = Objects.requireNonNull(leases);
        this.trace = Objects.requireNonNull(trace);
        monitor = new ProgressMonitor(Objects.requireNonNull(policy));
        utilityFailures = new UtilityFailures(blackboard);
    }

    public void begin(Sensors sensors) {
        this.sensors = Objects.requireNonNull(sensors);
        current = null;
        executedHook = null;
    }

    public void end() {
        current = null;
        trace.publish();
    }

    Node enter(Node node) {
        Node previous = current;
        current = node;
        return previous;
    }

    void leave(Node previous) {
        current = previous;
    }

    void executed(Hook hook) {
        executedHook = hook;
    }

    public Hook executedHook() {
        return executedHook;
    }

    void botStats(BotStats stats) {
        botStats = stats;
    }

    public NodeResult executeMonitor(Node node) {
        monitorDepth++;
        try {
            return node.execute(this);
        } finally {
            monitorDepth--;
        }
    }

    boolean monitoring() {
        return monitorDepth > 0;
    }

    public void event(Node node, Kind kind, FailureCode code, String reason, String detail) {
        trace.emit(new TraceEvent(tick(), nowMs(), node.hook(), node.owner(), node.path(), kind, code, reason, detail));
    }

    public void announceIdle(Node node, Consumer<String> alert) {
        if (idleAnnounced) return;
        idleAnnounced = true;
        event(node, Kind.RECOVERY, null, "safe idle", "leases=0");
        alert.accept("SafeIdle: resources released");
    }

    public void endIdlePeriod() {
        idleAnnounced = false;
    }

    public void progress(String reason) {
        progress(new ProgressEvidence(EvidenceKind.MILESTONE, 1, reason));
    }

    public boolean progress(ProgressEvidence evidence) {
        if (pluginAccess()) throw new IllegalStateException("Plugins cannot report progress evidence");
        if (current == null) throw new IllegalStateException("progress outside node execution");
        boolean accepted = monitor.accept(tick(), evidence);
        event(current, Kind.PROGRESS, null, evidence.reason(), "kind=" + evidence.kind() + " accepted=" + accepted);
        if (accepted) {
            transitions.progress();
            evidenceSequence++;
            if (botStats != null) botStats.evidence(evidence);
        }
        return accepted;
    }

    public String owner() {
        if (pluginOwner != null) return pluginOwner;
        if (current == null) throw new IllegalStateException("owner outside node execution");
        return current.owner();
    }

    public long tick() {
        return clock.tick();
    }

    public long nowMs() {
        return clock.nowMs();
    }

    public Clock clock() {
        return clock;
    }

    public Sensors sensors() {
        return Objects.requireNonNull(sensors, "tick has not begun");
    }

    public Blackboard bb() {
        return blackboard;
    }

    public Leases leases() {
        if (pluginAccess()) throw new IllegalStateException("Plugins cannot access resource leases");
        return leases;
    }

    public <T> T plugin(String owner, Supplier<T> work) {
        Objects.requireNonNull(work);
        if (owner == null || !owner.startsWith("plugin:") || owner.substring(7).isBlank()) {
            throw new IllegalArgumentException("invalid plugin owner");
        }
        String previous = pluginOwner;
        pluginOwner = owner;
        try {
            return work.get();
        } finally {
            pluginOwner = previous;
        }
    }

    private boolean pluginAccess() {
        return pluginOwner != null || current != null && current.owner().startsWith("plugin:");
    }

    public ProgressMonitor monitor() {
        return monitor;
    }

    public UtilityFailures utilityFailures() {
        return utilityFailures;
    }

    public TransitionLedger transitions() {
        return transitions;
    }

    public BudgetLedger budgets() {
        return budgets;
    }

    public long evidenceSequence() {
        return evidenceSequence;
    }

    public void stopHandler(Consumer<String> handler) {
        Objects.requireNonNull(handler);
        stopReportHandler((reason, detail) -> handler.accept(detail));
    }

    public void stopReportHandler(BiConsumer<ExitReason, String> handler) {
        stopHandler = Objects.requireNonNull(handler);
    }

    public void stopBot(String reason) {
        stopBot(ExitReason.STOPPED, reason);
    }

    public void stopBot(ExitReason reason, String detail) {
        if (stopHandler == null) throw new IllegalStateException("no bot stop handler installed");
        stopHandler.accept(Objects.requireNonNull(reason), Objects.requireNonNull(detail));
    }
}
