package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.nodes.Cooldown;
import com.hunterbuddy.behavior.nodes.Timeout;
import java.util.ArrayList;
import java.util.List;

public final class CooldownChecks {
    private final List<String> checks = new ArrayList<>();

    private CooldownChecks() {
    }

    public static List<String> run() {
        CooldownChecks check = new CooldownChecks();
        check.completion();
        check.keys();
        check.interruptions();
        check.fault();
        return List.copyOf(check.checks);
    }

    private Node prepare(Node node) {
        TreeValidator.validate(node);
        TreeValidator.bind(node, "cooldown", "bot:lab", Hook.BOT);
        return node;
    }

    private Node bounded(Node node) {
        return new Timeout(100, node);
    }

    private void require(boolean condition, String name) {
        if (!condition) throw new IllegalStateException("cooldown check failed: " + name);
        checks.add(name);
    }

    private boolean cooled(Node node, Simulation sim) {
        return sim.tick(node) instanceof Failure failure && failure.code() == FailureCode.COOLDOWN;
    }

    private void completion() {
        Simulation sim = new Simulation();
        SimulatedAction action = new SimulatedAction("work", ctx -> Success.INSTANCE);
        Node cooldown = prepare(new Cooldown(3, bounded(action)));
        require(sim.tick(cooldown) instanceof Success, "Cooldown permits first execution");
        require(cooled(cooldown, sim) && action.starts() == 1, "Cooldown refuses before starting child");
        cooldown.resetMemory();
        require(cooled(cooldown, sim) && action.starts() == 1, "Cooldown node reset preserves OWNER expiration");
        require(sim.tick(cooldown) instanceof Success && action.starts() == 2,
            "Cooldown refusals do not extend expiration");

        action = new SimulatedAction("failure", ctx -> new Failure(FailureCode.REFUSED, "refused"));
        cooldown = prepare(new Cooldown(3, bounded(action)));
        TreeValidator.bind(cooldown, "failed-cooldown", "bot:lab", Hook.BOT);
        require(sim.tick(cooldown) instanceof Failure failure && failure.code() == FailureCode.REFUSED,
            "Cooldown propagates original child failure");
        require(cooled(cooldown, sim) && action.starts() == 1, "Cooldown applies after child failure");

        action = new SimulatedAction("zero", ctx -> Success.INSTANCE);
        cooldown = prepare(new Cooldown(0, bounded(action)));
        sim.tick(cooldown);
        require(sim.tick(cooldown) instanceof Success && action.starts() == 2, "zero Cooldown permits consecutive execution");
    }

    private void keys() {
        Simulation sim = new Simulation();
        String[] target = {"a"};
        SimulatedAction action = new SimulatedAction("work", ctx -> Success.INSTANCE);
        Node cooldown = prepare(new Cooldown(10, bounded(action)).keyedBy(ctx -> target[0]));
        sim.tick(cooldown);
        target[0] = "b";
        require(sim.tick(cooldown) instanceof Success && action.starts() == 2, "Cooldown independent per target");
        target[0] = "a";
        require(cooled(cooldown, sim) && action.starts() == 2, "Cooldown returning target retains expiration");
        TreeValidator.bind(cooldown, "cooldown", "bot:other", Hook.BOT);
        require(sim.tick(cooldown) instanceof Success && action.starts() == 3, "Cooldown isolated per owner");
        TreeValidator.bind(cooldown, "different-path", "bot:lab", Hook.BOT);
        require(sim.tick(cooldown) instanceof Success && action.starts() == 4, "Cooldown isolated per node path");
    }

    private void fault() {
        Simulation sim = new Simulation();
        boolean[] faulty = {true};
        SimulatedAction action = new SimulatedAction("fault", ctx -> {
            if (faulty[0]) throw new IllegalStateException("child fault");
            return Success.INSTANCE;
        });
        Node cooldown = prepare(new Cooldown(10, bounded(action)));
        boolean thrown = false;
        try {
            sim.tick(cooldown);
        } catch (IllegalStateException ex) {
            thrown = "child fault".equals(ex.getMessage());
        }
        require(thrown && !cooldown.active() && action.cleanups() == 1,
            "Cooldown propagates fault and cleans child");
        faulty[0] = false;
        require(sim.tick(cooldown) instanceof Success && action.starts() == 2,
            "Cooldown fault does not manufacture terminal completion");
    }

    private void interruptions() {
        Simulation sim = new Simulation();
        boolean[] done = {false};
        SimulatedAction action = new SimulatedAction("work", ctx -> done[0] ? Success.INSTANCE : Running.WORKING);
        Node cooldown = prepare(new Cooldown(3, bounded(action)));
        sim.tick(cooldown);
        cooldown.halt(sim.ctx(), ExitReason.PREEMPTED);
        require(sim.tick(cooldown) instanceof Running && action.starts() == 2,
            "Cooldown interruption does not count as completion");
        done[0] = true;
        sim.tick(cooldown);
        require(cooled(cooldown, sim), "Cooldown starts from confirmed terminal result");
        require(sim.ctx().evidenceSequence() == 0, "Cooldown emits no fabricated progress");

        sim = new Simulation();
        String[] target = {"initial"};
        action = new SimulatedAction("target-change", ctx -> {
            target[0] = "next";
            return Success.INSTANCE;
        });
        cooldown = prepare(new Cooldown(10, bounded(action)).keyedBy(ctx -> target[0]));
        sim.tick(cooldown);
        require(sim.tick(cooldown) instanceof Success && action.starts() == 2,
            "Cooldown records executed target when child changes proposed target");
        target[0] = "initial";
        require(cooled(cooldown, sim), "Cooldown original target remains protected");
    }
}
