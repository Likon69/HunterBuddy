package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.ActivationScope;
import com.hunterbuddy.behavior.core.Blackboard;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressPolicy;
import com.hunterbuddy.behavior.core.Recovery;
import com.hunterbuddy.behavior.core.RootState;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.Tree;
import com.hunterbuddy.behavior.core.TreeRoot;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.hooks.TreeHooks;
import com.hunterbuddy.behavior.nodes.Budget;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Deadline;
import com.hunterbuddy.behavior.nodes.Delay;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Progress;
import com.hunterbuddy.behavior.nodes.ProgressGuard;
import com.hunterbuddy.behavior.nodes.ReactiveSelector;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WaitUntil;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.Lease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.LeaseState;
import com.hunterbuddy.behavior.resource.Leases;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.resource.ResourceArbiter;
import com.hunterbuddy.behavior.trace.Kind;
import com.hunterbuddy.behavior.trace.RingSink;
import com.hunterbuddy.behavior.trace.Trace;
import com.hunterbuddy.behavior.trace.TraceEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class SelfCheck {
    private final List<String> checks = new ArrayList<>();

    private SelfCheck() {
    }

    public static SelfCheckReport run() {
        SelfCheck check = new SelfCheck();
        check.lifecycle();
        check.preemption();
        check.composites();
        check.resources();
        check.budgets();
        check.blackboard();
        check.progress();
        check.validation();
        check.hookChanges();
        check.fault();
        check.checkRecovery();
        check.trace();
        check.checks.addAll(RegressionChecks.run());
        check.checks.addAll(CatalogChecks.run());
        check.checks.addAll(ParallelChecks.run());
        check.checks.addAll(UtilityChecks.run());
        check.checks.addAll(CooldownChecks.run());
        check.checks.addAll(UtilityRetryChecks.run());
        check.checks.addAll(CandidateChecks.run());
        check.checks.addAll(PickerIntegrationChecks.run());
        check.checks.addAll(TargetStabilityChecks.run());
        check.checks.addAll(RootReportChecks.run());
        check.checks.addAll(EngineChecks.run());
        check.checks.addAll(ObservabilityChecks.run());
        check.checks.addAll(DeathChecks.run());
        check.checks.addAll(SessionChecks.run());
        check.checks.addAll(LandingChecks.run());
        check.checks.addAll(FlightKeeperChecks.run());
        check.checks.addAll(IdleChecks.run());
        check.checks.addAll(IdleFailureChecks.run());
        return new SelfCheckReport(400, check.checks);
    }

    public static void main(String[] args) {
        run();
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("selfcheck failed: " + name);
        checks.add(name);
    }

    private Node prepare(Node node) {
        TreeValidator.validate(node);
        TreeValidator.bind(node, "test", "bot:lab", Hook.BOT);
        return node;
    }

    private Node bounded(Node node) {
        return new Timeout(1_000, node);
    }

    private Condition always() {
        return new Condition("always", sensors -> true);
    }

    private void failure(NodeResult result, FailureCode code, String name) {
        require(result instanceof Failure failed && failed.code() == code, name);
    }

    private void lifecycle() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("permanent");
        Node tree = prepare(bounded(action));
        for (int tick = 0; tick < 400; tick++) requireRunning(sim.tick(tree), "400 tick scenario");
        tree.halt(sim.ctx(), ExitReason.STOPPED);
        tree.halt(sim.ctx(), ExitReason.STOPPED);
        require(action.starts() == 1 && action.ticks() == 400 && action.stops().size() == 1
            && action.cleanups() == 1 && !action.active(), "one start/stop per activation over 400 ticks");

        SimulatedAction limited = new SimulatedAction("limited");
        limited.limit(2);
        Node limit = prepare(bounded(limited));
        sim.tick(limit);
        sim.tick(limit);
        failure(sim.tick(limit), FailureCode.TIMEOUT, "non-removable Action limit");
        require(!limited.active() && limited.cleanups() == 1, "Action timeout cleans up");

        List<Integer> order = new ArrayList<>();
        ActivationScope scope = new ActivationScope();
        scope.defer(() -> order.add(1));
        scope.defer(() -> { order.add(2); throw new IllegalStateException("second"); });
        scope.defer(() -> { order.add(3); throw new IllegalStateException("third"); });
        try {
            scope.close();
            throw new IllegalStateException("missing cleanup failure");
        } catch (IllegalStateException ex) {
            require(ex.getSuppressed().length == 1 && order.equals(List.of(3, 2, 1)), "LIFO cleanup aggregates failures");
        }
        scope.close();
        require(order.size() == 3, "cleanup is idempotent");
    }

    private void requireRunning(NodeResult result, String scenario) {
        if (!(result instanceof Running)) throw new IllegalStateException("selfcheck failed: " + scenario + " " + result);
    }

    private Node leased(Node node, Resource resource) {
        return new WithLease(Set.of(resource), LeasePolicy.WAIT, bounded(node));
    }

    private void preemption() {
        Simulation sim = new Simulation();
        SimulatedAction upper = new SimulatedAction("eat");
        SimulatedAction lower = new SimulatedAction("task");
        Node tree = prepare(new ReactiveSelector("reactive",
            new Guard(new Condition("eating", Sensors::eating), leased(upper, Resource.LOOK)),
            new Guard(always(), leased(lower, Resource.LOOK))));
        sim.tick(tree);
        sim.tick(tree, SimulatedSensors.capture(false, true, false, false, 0, 0));
        require(lower.stops().equals(List.of(ExitReason.PREEMPTED)) && upper.starts() == 1
            && upper.ticks() == 1 && sim.ctx().leases().snapshot().size() == 1, "same tick preemption releases before incoming lease");
        sim.tick(tree);
        require(upper.stops().equals(List.of(ExitReason.GUARD_LOST)) && lower.starts() == 2, "guard loss resumes lower branch same tick");
        tree.halt(sim.ctx(), ExitReason.STOPPED);
        require(sim.ctx().leases().snapshot().isEmpty() && !upper.active() && !lower.active(), "ancestor stop leaves no running descendant or lease");

        SimulatedAction held = new SimulatedAction("hold");
        Guard guard = new Guard(new Condition("eating", Sensors::eating), bounded(held)).hold(2);
        Node hold = prepare(new ReactiveSelector("hold-root", guard));
        sim.tick(hold, SimulatedSensors.capture(false, true, false, false, 0, 0));
        requireRunning(sim.tick(hold), "hold hysteresis");
        failure(sim.tick(hold), FailureCode.NO_OPTION, "hold expires then guard is lost");

        Guard cooldown = new Guard(always(), new Fail(FailureCode.NO_OPTION, "test")).onFailureCooldown(3);
        SimulatedAction backup = new SimulatedAction("backup");
        Node fallback = prepare(new ReactiveSelector("cooldown", cooldown, new Guard(always(), bounded(backup))));
        sim.tick(fallback);
        sim.tick(fallback);
        require(backup.starts() == 1, "failed urgent branch cooldown keeps fallback active");
        fallback.halt(sim.ctx(), ExitReason.STOPPED);
    }

    private void composites() {
        Simulation sim = new Simulation();
        require(sim.tick(prepare(new Sequence("empty"))) instanceof Success, "empty Sequence succeeds");
        failure(sim.tick(prepare(new Selector("empty"))), FailureCode.NO_OPTION, "empty Selector fails");
        SimulatedAction first = new SimulatedAction("first", ctx -> Success.INSTANCE);
        SimulatedAction second = new SimulatedAction("second");
        Node sequence = prepare(new Sequence("resumable", bounded(first), bounded(second)).resumable());
        sim.tick(sequence);
        sequence.halt(sim.ctx(), ExitReason.PREEMPTED);
        sim.tick(sequence);
        require(first.starts() == 1 && second.starts() == 2, "resumable Sequence preserves completed prefix");
        sequence.halt(sim.ctx(), ExitReason.STOPPED);
        sequence.resetMemory();
        sim.tick(sequence);
        require(first.starts() == 2, "explicit stop resets resumable Sequence memory");
        sequence.halt(sim.ctx(), ExitReason.STOPPED);
        Node fallback = prepare(new Selector("fallback", new Fail(FailureCode.REFUSED, "first"), new Succeed()));
        require(sim.tick(fallback) instanceof Success, "Selector falls through in same tick");
        Node wait = prepare(new WaitUntil(new Condition("eating", Sensors::eating), 2));
        sim.tick(wait);
        failure(sim.tick(wait), FailureCode.TIMEOUT, "WaitUntil is bounded");

        Node timeout = prepare(new Timeout(2, new SimulatedAction("timeout-resume")));
        sim.tick(timeout);
        timeout.halt(sim.ctx(), ExitReason.PAUSED);
        sim.tick(timeout);
        failure(sim.tick(timeout), FailureCode.TIMEOUT, "pause never refunds active Timeout");

        Node deadline = prepare(new Deadline(100, bounded(new SimulatedAction("deadline-resume"))));
        sim.tick(deadline);
        deadline.halt(sim.ctx(), ExitReason.PAUSED);
        sim.advance(100);
        failure(sim.tick(deadline), FailureCode.DEADLINE, "Deadline continues while paused");

        Node frozen = prepare(new Deadline(100, bounded(new SimulatedAction("deadline-frozen"))));
        sim.tick(frozen);
        frozen.halt(sim.ctx(), ExitReason.PAUSED);
        sim.advance(2_500);
        requireRunning(sim.tick(frozen), "Deadline credits freeze while paused");
        frozen.halt(sim.ctx(), ExitReason.STOPPED);
    }

    private void resources() {
        Leases arbiter = new ResourceArbiter();
        Lease legacy = arbiter.request("gate:legacy", Set.of(Resource.LOOK)).orElseThrow();
        require(arbiter.request("bot:lab", Set.of(Resource.BARITONE)).isEmpty()
            && arbiter.holder(Resource.MOVEMENT).isEmpty(), "lease acquisition is all-or-nothing with implications");
        arbiter.release(legacy);
        Lease path = arbiter.request("bot:lab", Set.of(Resource.BARITONE)).orElseThrow();
        require(path.resources().containsAll(Set.of(Resource.MOVEMENT, Resource.LOOK, Resource.SLOT)), "BARITONE implies movement/look/slot");
        arbiter.revoke(path);
        require(path.state() == LeaseState.REVOKING && arbiter.request("bot:next", Set.of(Resource.SLOT)).isEmpty(), "REVOKING still holds resources");
        arbiter.release(path);
        arbiter.release(path);
        require(arbiter.snapshot().isEmpty() && path.state() == LeaseState.RELEASED, "lease release is idempotent");

        Simulation sim = new Simulation();
        sim.ctx().leases().request("gate:legacy", Set.of(Resource.SLOT)).orElseThrow();
        SimulatedAction action = new SimulatedAction("blocked");
        Node waiting = prepare(leased(action, Resource.SLOT));
        for (int i = 0; i < 99; i++) requireRunning(sim.tick(waiting), "lease wait");
        failure(sim.tick(waiting), FailureCode.LEASE_TIMEOUT, "WAIT expires after 100 ticks");
        require(action.starts() == 0 && sim.ctx().leases().snapshot().size() == 1, "waiting never starts child or releases foreign lease");
        sim.ctx().leases().releaseAll();
        SimulatedAction revoked = new SimulatedAction("revoked");
        Node running = prepare(leased(revoked, Resource.LOOK));
        sim.tick(running);
        sim.ctx().leases().revoke(sim.ctx().leases().holder(Resource.LOOK).orElseThrow());
        failure(sim.tick(running), FailureCode.PREEMPTED, "revoked lease stops child before further work");
        require(revoked.ticks() == 1 && sim.ctx().leases().snapshot().isEmpty(), "revocation leaves no lease");
    }

    private void budgets() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("budgeted");
        Node budget = prepare(new Budget("attempt", 2, bounded(action)));
        sim.tick(budget);
        budget.halt(sim.ctx(), ExitReason.PREEMPTED);
        sim.tick(budget);
        budget.halt(sim.ctx(), ExitReason.PAUSED);
        sim.tick(prepare(new Progress("confirmed milestone")));
        failure(sim.tick(budget), FailureCode.BUDGET, "preemption, pause and progress never refund budget");
        sim.ctx().budgets().reset("bot:lab", "attempt");
        requireRunning(sim.tick(budget), "explicit reset");
        budget.halt(sim.ctx(), ExitReason.STOPPED);
        require(action.starts() == 3, "explicit ResetBudget permits another attempt");

        SimulatedAction loop = new SimulatedAction("repeat-target", ctx -> Success.INSTANCE);
        Node repeated = prepare(bounded(loop));
        for (int i = 0; i < 5; i++) require(sim.tick(repeated) instanceof Success, "action attempt " + i);
        failure(sim.tick(repeated), FailureCode.LOOP, "sixth Action start without proof is LOOP");
        sim.tick(prepare(new Progress("task success")));
        require(sim.tick(repeated) instanceof Success, "accepted evidence resets transition ledger");
    }

    private void blackboard() {
        Blackboard bb = new Blackboard();
        Key<Integer> value = new Key<>("value", Integer.class, Scope.OWNER, false);
        Key<Integer> durable = new Key<>("failed-target", Integer.class, Scope.OWNER, true);
        bb.set("bot:first", value, 1);
        bb.set("bot:second", value, 2);
        require(bb.get("bot:first", value).orElseThrow() == 1 && bb.get("bot:second", value).orElseThrow() == 2, "OWNER namespaces are isolated");
        bb.set("bot:first", durable, 3);
        bb.removeOwner("bot:first");
        bb.restoreOwner("bot:first");
        require(bb.get("bot:first", value).isEmpty() && bb.get("bot:first", durable).orElseThrow() == 3, "owner removal retains only durable keys in WORLD");
        bb.world("other-world");
        bb.removeOwner("bot:first");
        bb.world("third-world");
        bb.restoreOwner("bot:first");
        require(bb.get("bot:first", durable).isEmpty(), "WORLD namespaces are isolated");

        Simulation sim = new Simulation();
        Key<Integer> taskValue = new Key<>("task", Integer.class, Scope.TASK, false);
        SimulatedAction writer = new SimulatedAction("task-writer", ctx -> {
            ctx.bb().set(ctx.owner(), taskValue, 7);
            return Running.WORKING;
        });
        Node task = prepare(new TaskScope(bounded(writer)));
        sim.tick(task);
        require(sim.ctx().bb().get("bot:lab", taskValue).orElseThrow() == 7, "TASK lives during activation");
        task.halt(sim.ctx(), ExitReason.PREEMPTED);
        try {
            sim.ctx().bb().get("bot:lab", taskValue);
            throw new IllegalStateException("task remained open");
        } catch (IllegalStateException ex) {
            require(ex.getMessage().startsWith("no TASK"), "TASK closes after descendants stop");
        }
    }

    private void progress() {
        Simulation sim = new Simulation();
        sim.advance(0);
        for (int i = 0; i < 179; i++) {
            sim.advance(1_000);
            sim.ctx().monitor().sample(sim.ctx().clock(), SimulatedSensors.idle());
        }
        require(!sim.ctx().monitor().fired(), "monitor waits full 180 seconds");
        sim.advance(1_000);
        sim.ctx().monitor().sample(sim.ctx().clock(), SimulatedSensors.idle());
        require(sim.ctx().monitor().fired(), "monitor fires at 180 seconds active");
        long inactivity = sim.ctx().monitor().inactiveMs();
        sim.advance(2_500);
        sim.ctx().monitor().sample(sim.ctx().clock(), SimulatedSensors.idle());
        require(sim.ctx().monitor().inactiveMs() == inactivity, "client freezes above 2000ms are credited");
        Node proof = prepare(new Progress("confirmed"));
        for (int i = 0; i < 20; i++) sim.tick(proof, SimulatedSensors.capture(false, false, false, false, 0, 1));
        require(sim.ctx().monitor().fired(), "setback rejects evidence during 20 ticks");
        sim.tick(proof, SimulatedSensors.capture(false, false, false, false, 0, 1));
        require(sim.ctx().monitor().inactiveMs() == 0, "evidence accepted after setback window");
        SimulatedAction stalled = new SimulatedAction("stalled");
        Node guard = prepare(new ProgressGuard(Sensors::y, 1, 3, stalled));
        sim.tick(guard);
        sim.tick(guard);
        failure(sim.tick(guard), FailureCode.NO_PROGRESS, "ProgressGuard bounds stagnant Action");
        require(stalled.stops().equals(List.of(ExitReason.ABORTED)), "ProgressGuard aborts child");
    }

    private void rejects(Node node, String reason) {
        try {
            TreeValidator.validate(node);
            throw new IllegalStateException("invalid tree accepted");
        } catch (IllegalArgumentException ex) {
            require(ex.getMessage().contains(reason), "validator rejects " + reason);
        }
    }

    private void validation() {
        rejects(new SimulatedAction("unbounded"), "Action without Timeout/ProgressGuard");
        rejects(new ReactiveSelector("unguarded", new Succeed()), "ReactiveSelector child without Guard");
        Node shared = new Succeed();
        rejects(new Sequence("duplicate", shared, shared), "double parent");
        rejects(new WithLease(Set.of(), LeasePolicy.WAIT, new Succeed()), "empty WithLease");
        Recovery recovery = new Recovery(new Fail(FailureCode.NO_OPTION, "none"), new Succeed(), message -> { });
        require(Tree.of(new Succeed(), recovery).recovery() == recovery, "validated Tree includes separate recovery");
    }

    private HookEntry entry(Hook hook, String owner, Contributor contributor, Condition condition, boolean flightAware, Node node) {
        return new HookEntry(hook, owner, contributor, 0, new Guard(condition, node), flightAware, node);
    }

    private Recovery recovery() {
        return new Recovery(new Fail(FailureCode.NO_OPTION, "no alternative"), new Succeed(), message -> { });
    }

    private void hookChanges() {
        Simulation sim = new Simulation();
        List<String> refused = new ArrayList<>();
        TreeHooks hooks = new TreeHooks(refused::add);
        SimulatedAction bot = new SimulatedAction("task");
        hooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(), false, leased(bot, Resource.LOOK)));
        hooks.insert(entry(Hook.SESSION, "core:session", Contributor.CORE, new Condition("dead", Sensors::dead), true, new Delay(20)));
        hooks.applyPendingChanges(sim.ctx());
        require(hooks.entries().getFirst().hook() == Hook.SESSION, "root hooks follow fixed order regardless of insertion");
        TreeRoot root = new TreeRoot(sim.ctx(), hooks, recovery());
        root.start();
        sim.tick(root, SimulatedSensors.capture(false, false, false, true, 0, 0));
        require(bot.starts() == 0 && sim.ctx().leases().snapshot().isEmpty(), "non-flightAware hook never starts in gliding");
        sim.tick(root, SimulatedSensors.idle());
        String stablePath = bot.path();
        Node detachedRoot = hooks.build();
        hooks.insert(entry(Hook.BOT_PRE, "plugin:signal", Contributor.PLUGIN, new Condition("disabled", sensors -> false), true, new Succeed()));
        sim.tick(root, SimulatedSensors.idle());
        require(bot.starts() == 1 && bot.stops().isEmpty() && bot.path().equals(stablePath), "hook rebuild preserves retained active branch and stable path");
        require(!detachedRoot.active() && detachedRoot.children().stream().noneMatch(Node::active), "retained branch is detached from retired root");
        Node previousRoot = hooks.build();
        Node plugin = leased(new SimulatedAction("illegal-plugin"), Resource.MOVEMENT);
        hooks.insert(entry(Hook.BOT_PRE, "plugin:test", Contributor.PLUGIN, always(), true, plugin));
        sim.tick(root, SimulatedSensors.idle());
        require(hooks.build() == previousRoot && bot.starts() == 1 && !refused.isEmpty()
            && refused.getLast().contains("PLUGIN"), "plugin resource request rejected without changing active root");
        hooks.insert(entry(Hook.SESSION, "bot:illegal", Contributor.BOT, always(), true, new Succeed()));
        sim.tick(root, SimulatedSensors.idle());
        require(hooks.build() == previousRoot && refused.getLast().contains("forbidden"), "forbidden hook contributor is rejected");
        hooks.remove("bot:lab");
        require(bot.active(), "hook removal is queued");
        sim.tick(root, SimulatedSensors.idle());
        require(!bot.active() && sim.ctx().leases().snapshot().isEmpty() && bot.stops().equals(List.of(ExitReason.STOPPED)), "active hook removal halts and releases at end of tick");
        root.stop(ExitReason.STOPPED);

        TreeHooks queuedHooks = new TreeHooks(refused::add);
        TreeRoot queuedRoot = new TreeRoot(sim.ctx(), queuedHooks, recovery());
        SimulatedAction stop = new SimulatedAction("stop-during-tick", ctx -> {
            queuedRoot.stop(ExitReason.STOPPED);
            return Running.WORKING;
        });
        queuedHooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(), true, leased(stop, Resource.LOOK)));
        queuedHooks.applyPendingChanges(sim.ctx());
        queuedRoot.start();
        sim.tick(queuedRoot, SimulatedSensors.idle());
        require(queuedRoot.state() == RootState.STOPPED && stop.cleanups() == 1
            && sim.ctx().leases().snapshot().isEmpty(), "stop during tick applies after node returns");
    }

    private void fault() {
        Simulation sim = new Simulation();
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalStateException(message); });
        SimulatedAction fault = new SimulatedAction("fault", ctx -> { throw new IllegalStateException("injected"); });
        hooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(), true, leased(fault, Resource.BARITONE)));
        hooks.applyPendingChanges(sim.ctx());
        TreeRoot root = new TreeRoot(sim.ctx(), hooks, recovery());
        root.start();
        sim.tick(root, SimulatedSensors.idle());
        require(root.state() == RootState.STOPPED && fault.stops().equals(List.of(ExitReason.FAULT))
            && fault.cleanups() == 1 && sim.ctx().leases().snapshot().isEmpty(), "injected exception stops root and releases all resources");
        require(sim.ring().snapshot().stream().anyMatch(event -> event.kind() == Kind.FAULT), "injected exception produces FAULT trace");
    }

    private void checkRecovery() {
        Simulation sim = new Simulation(new ProgressPolicy(Set.of(EvidenceKind.MILESTONE), 100));
        TreeHooks hooks = new TreeHooks(message -> { throw new IllegalStateException(message); });
        SimulatedAction stalled = new SimulatedAction("stalled-task");
        hooks.insert(entry(Hook.BOT, "bot:lab", Contributor.BOT, always(), true, leased(stalled, Resource.LOOK)));
        hooks.applyPendingChanges(sim.ctx());
        TreeRoot root = new TreeRoot(sim.ctx(), hooks, recovery());
        root.start();
        for (int i = 0; i < 20 && root.recoveryLevel() != 4; i++) sim.tick(root, SimulatedSensors.idle());
        require(root.recoveryLevel() == 4 && sim.ctx().leases().snapshot().isEmpty(), "no progress reaches SafeIdle within four recovery levels");
        List<TraceEvent> events = sim.ring().snapshot();
        require(events.stream().anyMatch(event -> event.kind() == Kind.MONITOR)
            && events.stream().anyMatch(event -> event.kind() == Kind.RECOVERY && event.reason().equals("safe idle")), "monitor firing is followed by recovery trace");
        root.stop(ExitReason.STOPPED);
    }

    private void trace() {
        RingSink ring = new RingSink(2);
        Trace trace = new Trace(ring);
        for (int i = 0; i < 3; i++) trace.emit(new TraceEvent(i, i * 50, Hook.BOT, "bot:lab", "test", Kind.START, null, "start", ""));
        require(ring.snapshot().isEmpty(), "trace publishes at tick boundary");
        trace.publish();
        require(ring.snapshot().size() == 2 && ring.snapshot().getFirst().tick() == 1, "trace ring keeps most recent bounded events");
    }
}
