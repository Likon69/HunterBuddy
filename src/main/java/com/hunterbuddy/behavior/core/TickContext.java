package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.resource.ResourceArbiter;
import com.hunterbuddy.behavior.trace.Kind;
import com.hunterbuddy.behavior.trace.Trace;
import com.hunterbuddy.behavior.trace.TraceEvent;
import java.util.Objects;
import java.util.function.Consumer;

public final class TickContext {
    public static final Key<String> CURRENT_TARGET = new Key<>("current-target", String.class, Scope.OWNER, false);
    private final Clock clock;
    private final Blackboard blackboard;
    private final ResourceArbiter leases;
    private final Trace trace;
    private final ProgressMonitor monitor;
    private final TransitionLedger transitions = new TransitionLedger();
    private final BudgetLedger budgets = new BudgetLedger();
    private Sensors sensors;
    private Node current;
    private long evidenceSequence;
    private Consumer<String> stopHandler;

    public TickContext(Clock clock, Blackboard blackboard, ResourceArbiter leases, Trace trace, ProgressPolicy policy) {
        this.clock = Objects.requireNonNull(clock);
        this.blackboard = Objects.requireNonNull(blackboard);
        this.leases = Objects.requireNonNull(leases);
        this.trace = Objects.requireNonNull(trace);
        monitor = new ProgressMonitor(Objects.requireNonNull(policy));
    }

    public void begin(Sensors sensors) {
        this.sensors = Objects.requireNonNull(sensors);
        current = null;
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

    public void event(Node node, Kind kind, FailureCode code, String reason, String detail) {
        trace.emit(new TraceEvent(tick(), nowMs(), node.hook(), node.owner(), node.path(), kind, code, reason, detail));
    }

    public void progress(String reason) {
        progress(new ProgressEvidence(EvidenceKind.MILESTONE, 1, reason));
    }

    public boolean progress(ProgressEvidence evidence) {
        if (current == null) throw new IllegalStateException("progress outside node execution");
        boolean accepted = monitor.accept(tick(), evidence);
        event(current, Kind.PROGRESS, null, evidence.reason(), "kind=" + evidence.kind() + " accepted=" + accepted);
        if (accepted) {
            transitions.progress();
            evidenceSequence++;
        }
        return accepted;
    }

    public String owner() {
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

    public ResourceArbiter leases() {
        return leases;
    }

    public ProgressMonitor monitor() {
        return monitor;
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
        stopHandler = Objects.requireNonNull(handler);
    }

    public void stopBot(String reason) {
        if (stopHandler == null) throw new IllegalStateException("no bot stop handler installed");
        stopHandler.accept(Objects.requireNonNull(reason));
    }
}
