package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.hooks.Hook;
import com.hunterbuddy.behavior.nodes.ClearScope;
import com.hunterbuddy.behavior.nodes.MarkFailed;
import com.hunterbuddy.behavior.nodes.ResetBudget;
import com.hunterbuddy.behavior.nodes.SafeIdle;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.StopBot;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.function.Consumer;

public final class Recovery extends Composite {
    private int level;
    private final RecoveryContext target;
    private final RestartBudget restarts = new RestartBudget();
    private boolean restartReady;
    private int activeLevel;
    private final Node levelTwo;
    private final Node levelThree;

    public Recovery(Node levelTwo, Node levelThree, Consumer<String> alert) {
        this(new RecoveryContext(), levelTwo, levelThree, alert);
    }

    private Recovery(RecoveryContext target, Node levelTwo, Node levelThree, Consumer<String> alert) {
        super("recovery",
            new Sequence("reset-task", new ClearScope(Scope.TASK, ctx -> target.taskOwner()), new Succeed()),
            new Timeout(12_000, levelTwo),
            new Timeout(12_000, new Sequence("alternative", new MarkFailed(TickContext.CURRENT_TARGET, ctx -> target.taskOwner()),
                new ResetBudget("all", ctx -> target.taskOwner()), levelThree, new Succeed())),
            new SafeIdle(alert),
            new StopBot("automatic restart limit reached"));
        this.target = target;
        this.levelTwo = levelTwo;
        this.levelThree = levelThree;
    }

    public void next(TickContext ctx) {
        halt(ctx, ExitReason.ABORTED);
        level = Math.min(4, level + 1);
        ctx.event(this, Kind.RECOVERY, null, "enter", "level=" + level);
    }

    public void reset() {
        if (active()) throw new IllegalStateException("cannot reset active recovery");
        level = 0;
        restartReady = false;
    }

    public int level() {
        return level;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (activeLevel < 1) return fail(FailureCode.FAULT, "recovery level is not set");
        NodeResult result = children().get(activeLevel - 1).execute(ctx);
        if (activeLevel != 4 || !(result instanceof Success)) return result;
        if (!restarts.tryRestart(ctx.nowMs())) return children().get(4).execute(ctx);
        restartReady = true;
        ctx.event(this, Kind.BOT, null, "restart", "level=4");
        return result;
    }

    @Override
    protected void onStart(TickContext ctx) {
        restartReady = false;
        activeLevel = level;
    }

    public void taskOwner(String owner) {
        if (active() && !target.taskOwner().equals(owner)) throw new IllegalStateException("cannot change active recovery owner");
        target.taskOwner(owner);
        if (!active()) {
            TreeValidator.bind(levelTwo, levelTwo.path(), owner, Hook.RECOVERY);
            TreeValidator.bind(levelThree, levelThree.path(), owner, Hook.RECOVERY);
        }
    }

    public String taskOwner() {
        return target.taskOwner();
    }

    public boolean restartReady() {
        return restartReady;
    }

    public int restartCount() {
        return restarts.count();
    }

    public void provenProgress() {
        level = 0;
    }
}
