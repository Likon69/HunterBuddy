package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.bot.Bot;
import com.hunterbuddy.behavior.bot.BotConfig;
import com.hunterbuddy.behavior.bot.BotContext;
import com.hunterbuddy.behavior.bot.BotStatus;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.StopReport;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.hooks.Contributor;
import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.hooks.HookEntry;
import com.hunterbuddy.behavior.mc.LabSettings;
import com.hunterbuddy.behavior.mc.WorldIdentity;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.StopBot;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import meteordevelopment.meteorclient.settings.SettingGroup;

public final class LiveLabBot implements Bot {
    private final LabSettings settings;
    private final LabTask task;
    private final WorldReader world;
    private final Outputs outputs;
    private WorldIdentity identity;
    private Stage stage = Stage.READY;
    private long completed;

    public LiveLabBot(LabSettings settings, LabTask task, WorldReader world, Outputs outputs) {
        this.settings = Objects.requireNonNull(settings);
        this.task = Objects.requireNonNull(task);
        this.world = Objects.requireNonNull(world);
        this.outputs = Objects.requireNonNull(outputs);
        identity = world.identity();
    }

    @Override
    public String name() {
        return "lab";
    }

    @Override
    public String description() {
        return "Live one-shot lab " + task.name() + ": " + task.target();
    }

    @Override
    public void onRegister(SettingGroup settings) {
        this.settings.register(settings);
    }

    private void requireContext(BotContext context) {
        Objects.requireNonNull(context);
        if (!context.owner().equals("bot:" + name())) throw new IllegalArgumentException("wrong live LabBot owner");
    }

    @Override
    public HookEntry taskEntry(BotContext context) {
        requireContext(context);
        if (stage != Stage.READY) throw new IllegalStateException("LiveLabBot is already installed");
        Node work = new LabTaskNode(task.build(context, world, outputs), () -> {
            completed++;
            stage = Stage.COMPLETE;
        }, task.recoverFailure() ? () -> stage = Stage.UNREACHABLE : null);
        return new HookEntry(Hook.BOT, context.owner(), Contributor.BOT, 0,
            new Guard(new Condition("live-lab-ready", sensors -> stage == Stage.RUNNING), work), task.flightAware(), work);
    }

    @Override
    public List<HookEntry> extraEntries(BotContext context) {
        requireContext(context);
        Node finish = new Sequence("lab-finished", new Succeed(), new StopBot(ExitReason.COMPLETED, "Lab " + task.name() + " completed"));
        return List.of(new HookEntry(Hook.BOT_POST, context.owner(), Contributor.BOT, 0,
            new Guard(new Condition("live-lab-complete", sensors -> stage == Stage.COMPLETE), finish), false, finish));
    }

    @Override
    public BotConfig config() {
        return new BotConfig(false, LabBot.CONFIG.deathPolicy(), settings.resumeAfterReconnect(), LabBot.CONFIG.progress(), LabBot.CONFIG.defaultActionLimitTicks());
    }

    @Override
    public void onStart(BotContext context) {
        requireContext(context);
        if (stage != Stage.READY) throw new IllegalStateException("LiveLabBot is already installed");
        context.ticks().bb().set(context.owner(), TickContext.CURRENT_TARGET, task.target());
        task.initialize(context);
        stage = Stage.RUNNING;
    }

    @Override
    public void onStop(StopReport report) {
        Objects.requireNonNull(report);
        if (!report.owner().equals("bot:" + name())) throw new IllegalArgumentException("wrong live LabBot stop owner");
        stage = Stage.STOPPED;
    }

    @Override
    public BotStatus status() {
        return new BotStatus(stage == Stage.UNREACHABLE ? task.name() + " unreachable" : task.name(), task.target(), completed);
    }

    public WorldIdentity identity() {
        return identity;
    }

    @Override
    public void onResume(BotContext context) {
        requireContext(context);
        identity = world.identity();
        context.ticks().bb().set(context.owner(), TickContext.CURRENT_TARGET, task.target());
        task.initialize(context);
        if (stage != Stage.COMPLETE) stage = Stage.RUNNING;
    }

    public Set<Resource> resources() {
        return task.resources();
    }

    private enum Stage {
        READY, RUNNING, COMPLETE, UNREACHABLE, STOPPED
    }
}
