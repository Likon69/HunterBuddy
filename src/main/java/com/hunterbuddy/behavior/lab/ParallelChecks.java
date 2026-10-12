package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.nodes.Budget;
import com.hunterbuddy.behavior.nodes.Parallel;
import com.hunterbuddy.behavior.nodes.ParallelPolicy;
import com.hunterbuddy.behavior.nodes.Repeat;
import com.hunterbuddy.behavior.nodes.RestartPolicy;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ParallelChecks {
    private final List<String> checks = new ArrayList<>();

    private ParallelChecks() {
    }

    public static List<String> run() {
        ParallelChecks check = new ParallelChecks();
        check.main();
        check.all();
        check.restarts();
        check.cleanup();
        return List.copyOf(check.checks);
    }

    private Node prepare(Node node) {
        TreeValidator.validate(node);
        TreeValidator.bind(node, "parallel", "bot:lab", Hook.BOT);
        return node;
    }

    private Node bounded(Node node) {
        return new Timeout(100, node);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("parallel check failed: " + name);
        checks.add(name);
    }

    private void main() {
        Simulation sim = new Simulation();
        List<String> order = new ArrayList<>();
        boolean[] done = {false};
        SimulatedAction principal = new SimulatedAction("principal", ctx -> {
            order.add("main");
            return done[0] ? Success.INSTANCE : Running.WORKING;
        });
        SimulatedAction monitor = new SimulatedAction("monitor", ctx -> {
            order.add("monitor");
            return Running.WORKING;
        });
        Node tree = prepare(new Parallel("main", ParallelPolicy.MAIN, RestartPolicy.ONCE,
            bounded(principal), bounded(monitor)));
        require(sim.tick(tree) instanceof Running && order.equals(List.of("main", "monitor")),
            "MAIN ticks principal before monitors");
        done[0] = true;
        require(sim.tick(tree) instanceof Success && monitor.ticks() == 1
            && monitor.stops().equals(List.of(ExitReason.COMPLETED)), "MAIN success halts monitors before ticking them");
        require(principal.cleanups() == 1 && monitor.cleanups() == 1, "MAIN closes all activations once");
        boolean rejected = false;
        try {
            new Parallel("empty", ParallelPolicy.MAIN, RestartPolicy.ONCE);
        } catch (IllegalArgumentException ex) {
            rejected = true;
        }
        require(rejected, "empty MAIN rejected");
    }

    private void all() {
        Simulation sim = new Simulation();
        boolean[] done = {false};
        SimulatedAction first = new SimulatedAction("first", ctx -> Success.INSTANCE);
        SimulatedAction second = new SimulatedAction("second", ctx -> done[0] ? Success.INSTANCE : Running.WORKING);
        Node tree = prepare(new Parallel("all", ParallelPolicy.ALL, RestartPolicy.ONCE, bounded(first), bounded(second)));
        sim.tick(tree);
        sim.tick(tree);
        require(first.starts() == 1 && second.ticks() == 2, "ALL does not restart successful children");
        done[0] = true;
        require(sim.tick(tree) instanceof Success && first.starts() == 1, "ALL succeeds only after every child succeeds");
        require(sim.tick(prepare(new Parallel("empty", ParallelPolicy.ALL, RestartPolicy.ONCE))) instanceof Success,
            "empty ALL succeeds");

        first = new SimulatedAction("running");
        second = new SimulatedAction("failure", ctx -> new Failure(FailureCode.REFUSED, "refused"));
        SimulatedAction unvisited = new SimulatedAction("unvisited");
        tree = prepare(new Parallel("fail-fast", ParallelPolicy.ALL, RestartPolicy.ONCE,
            bounded(first), bounded(second), bounded(unvisited)));
        require(sim.tick(tree) instanceof Failure failure && failure.code() == FailureCode.REFUSED
            && first.stops().equals(List.of(ExitReason.FAILED)) && unvisited.starts() == 0,
            "ALL first failure stops siblings and skips later children");

        first = new SimulatedAction("restart-after-pause", ctx -> Success.INSTANCE);
        second = new SimulatedAction("waiting");
        tree = prepare(new Parallel("paused", ParallelPolicy.ALL, RestartPolicy.ONCE, bounded(first), bounded(second)));
        sim.tick(tree);
        tree.halt(sim.ctx(), ExitReason.PAUSED);
        sim.tick(tree);
        require(first.starts() == 2 && second.starts() == 2, "Parallel restarts all children after pause");
        tree.halt(sim.ctx(), ExitReason.STOPPED);
    }

    private void restarts() {
        Simulation sim = new Simulation();
        SimulatedAction principal = new SimulatedAction("principal");
        SimulatedAction monitor = new SimulatedAction("monitor", ctx -> new Failure(FailureCode.NOT_CONFIRMED, "sample"));
        Node tree = prepare(new Parallel("once", ParallelPolicy.MAIN, RestartPolicy.ONCE,
            bounded(principal), bounded(monitor)));
        sim.tick(tree);
        sim.tick(tree);
        require(monitor.starts() == 1 && principal.ticks() == 2, "MAIN ONCE keeps terminal monitor stopped");
        tree.halt(sim.ctx(), ExitReason.STOPPED);

        principal = new SimulatedAction("restarted-principal");
        monitor = new SimulatedAction("restarted-monitor", ctx -> Success.INSTANCE);
        tree = prepare(new Parallel("restart", ParallelPolicy.MAIN, RestartPolicy.restart(2),
            bounded(principal), bounded(monitor)));
        for (int i = 0; i < 6; i++) sim.tick(tree);
        require(monitor.starts() == 3 && principal.ticks() == 6, "MAIN monitor restarts limited to configured maximum");
        tree.halt(sim.ctx(), ExitReason.STOPPED);

        principal = new SimulatedAction("untracked-main");
        monitor = new SimulatedAction("untracked-monitor", ctx -> Success.INSTANCE);
        tree = prepare(new Parallel("monitor-transitions", ParallelPolicy.MAIN, RestartPolicy.restart(6),
            bounded(principal), bounded(monitor)));
        for (int i = 0; i < 8; i++) sim.tick(tree);
        require(monitor.starts() == 7, "MAIN monitors excluded from action transition loop ledger");
        tree.halt(sim.ctx(), ExitReason.STOPPED);

        principal = new SimulatedAction("budget-main");
        monitor = new SimulatedAction("budget-monitor", ctx -> Success.INSTANCE);
        tree = prepare(new Parallel("budget", ParallelPolicy.MAIN, RestartPolicy.restart(2), bounded(principal),
            new Budget("monitor", 1, bounded(monitor))));
        for (int i = 0; i < 4; i++) sim.tick(tree);
        require(monitor.starts() == 1, "monitor restart does not refund Budget ledger");
        tree.halt(sim.ctx(), ExitReason.STOPPED);
    }

    private void cleanup() {
        Simulation sim = new Simulation();
        boolean[] fail = {false};
        SimulatedAction principal = new SimulatedAction("principal", ctx -> fail[0]
            ? new Failure(FailureCode.REFUSED, "refused") : Running.WORKING);
        SimulatedAction monitor = new SimulatedAction("monitor");
        Node tree = prepare(new Parallel("leases", ParallelPolicy.MAIN, RestartPolicy.ONCE,
            bounded(principal), new WithLease(Set.of(Resource.SLOT), LeasePolicy.WAIT, bounded(monitor))));
        sim.tick(tree);
        require(!sim.ctx().leases().snapshot().isEmpty(), "monitor acquires resource");
        fail[0] = true;
        require(sim.tick(tree) instanceof Failure && sim.ctx().leases().snapshot().isEmpty()
            && monitor.cleanups() == 1, "principal failure releases monitor lease");

        principal = new SimulatedAction("fault-principal");
        monitor = new SimulatedAction("fault-monitor", ctx -> {
            throw new IllegalStateException("monitor fault");
        });
        tree = prepare(new Parallel("fault", ParallelPolicy.MAIN, RestartPolicy.ONCE, bounded(principal),
            new WithLease(Set.of(Resource.SLOT), LeasePolicy.WAIT, bounded(monitor))));
        boolean thrown = false;
        try {
            sim.tick(tree);
        } catch (IllegalStateException ex) {
            thrown = "monitor fault".equals(ex.getMessage());
        }
        require(thrown && !tree.active() && !principal.active() && !monitor.active()
            && sim.ctx().leases().snapshot().isEmpty(), "monitor exception cleans entire Parallel");
        Node repeated = prepare(new Repeat(8,
            bounded(new SimulatedAction("after-monitor-fault", ctx -> Success.INSTANCE))));
        NodeResult result = Running.WORKING;
        for (int i = 0; i < 8 && result instanceof Running; i++) result = sim.tick(repeated);
        require(result instanceof Failure failure && failure.code() == FailureCode.LOOP,
            "monitor execution context restored after exception");
    }
}
