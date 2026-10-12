package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.Bot;
import com.hunterbuddy.behavior.bot.BotStatus;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.UtilityOption;
import com.hunterbuddy.behavior.nodes.UtilitySelector;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Objects;
import java.util.Set;

public final class LabBot implements Bot {
    private final LabScenario scenario;
    private int ticks;
    private long completed;

    public LabBot(LabScenario scenario) {
        this.scenario = Objects.requireNonNull(scenario);
    }

    @Override
    public String name() {
        return "lab";
    }

    @Override
    public String description() {
        return "Simulated behavior only: " + scenario;
    }

    @Override
    public HookEntry taskEntry() {
        Node work = new Timeout(1_200, new WithLease(Set.of(Resource.MOVEMENT), LeasePolicy.WAIT,
            new SimulatedAction("simulated-" + scenario, this::work)));
        Node task = new UtilitySelector("lab-task", new UtilityOption(work, ctx -> 1, ctx -> "simulated:" + scenario));
        return new HookEntry(Hook.BOT, "bot:lab", Contributor.BOT, 0,
            new Guard(new Condition("lab-ready", sensors -> true), task), true, task);
    }

    private NodeResult work(TickContext ctx) {
        ticks++;
        if (scenario == LabScenario.FAULT) throw new IllegalStateException("Simulated LabBot fault");
        if (scenario == LabScenario.STALLED || ticks % 40 != 0) return new Running("simulated lab work");
        completed++;
        ctx.progress("simulated lab milestone");
        return Success.INSTANCE;
    }

    @Override
    public BotStatus status() {
        return new BotStatus(scenario.name(), "simulated:" + scenario, completed);
    }
}
