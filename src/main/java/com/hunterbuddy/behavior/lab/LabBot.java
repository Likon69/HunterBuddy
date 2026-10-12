package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.Bot;
import com.hunterbuddy.behavior.bot.BotConfig;
import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.bot.BotStatus;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressPolicy;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.StopReport;
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
import com.hunterbuddy.behavior.routine.DeathPolicy;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class LabBot implements Bot {
    private static final BotConfig CONFIG = new BotConfig(false, DeathPolicy.STOP, false, ProgressPolicy.DEFAULT, 1_200);
    private final LabScenario scenario;
    private BotContext context;
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
    public HookEntry taskEntry(BotContext context) {
        Objects.requireNonNull(context);
        if (!context.owner().equals("bot:" + name())) throw new IllegalArgumentException("wrong LabBot owner");
        if (this.context != null) throw new IllegalStateException("LabBot already started");
        Node work = new Timeout(context.config().defaultActionLimitTicks(), new WithLease(Set.of(Resource.MOVEMENT), LeasePolicy.WAIT,
            new SimulatedAction("simulated-" + scenario, this::work)));
        Node task = new UtilitySelector("lab-task", new UtilityOption(work, ctx -> 1, ctx -> "simulated:" + scenario));
        return new HookEntry(Hook.BOT, context.owner(), Contributor.BOT, 0,
            new Guard(new Condition("lab-ready", sensors -> this.context != null), task), true, task);
    }

    @Override
    public List<HookEntry> extraEntries(BotContext context) {
        Objects.requireNonNull(context);
        if (!context.owner().equals("bot:" + name())) throw new IllegalArgumentException("wrong LabBot owner");
        return List.of();
    }

    @Override
    public BotConfig config() {
        return CONFIG;
    }

    @Override
    public void onStart(BotContext context) {
        Objects.requireNonNull(context);
        if (!context.owner().equals("bot:" + name())) throw new IllegalArgumentException("wrong LabBot owner");
        if (this.context != null) throw new IllegalStateException("LabBot already started");
        this.context = context;
        ticks = 0;
        completed = 0;
    }

    @Override
    public void onStop(StopReport report) {
        Objects.requireNonNull(report);
        if (!report.owner().equals("bot:" + name())) throw new IllegalArgumentException("wrong LabBot stop owner");
        context = null;
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
