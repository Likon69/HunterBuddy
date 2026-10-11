package com.hunterbuddy.behavior.core;

import com.hunterbuddy.behavior.nodes.ClearScope;
import com.hunterbuddy.behavior.nodes.ResetBudget;
import com.hunterbuddy.behavior.nodes.SafeIdle;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.function.Consumer;

public final class Recovery extends Composite {
    private int level;

    public Recovery(Node levelTwo, Node levelThree, Consumer<String> alert) {
        super("recovery",
            new Sequence("reset-task", new ClearScope(Scope.TASK), new Succeed()),
            new Timeout(12_000, levelTwo),
            new Timeout(12_000, new Sequence("alternative", new ResetBudget("all"), levelThree, new Succeed())),
            new SafeIdle(alert));
    }

    public void next(TickContext ctx) {
        halt(ctx, ExitReason.ABORTED);
        level = Math.min(4, level + 1);
        ctx.event(this, Kind.RECOVERY, null, "enter", "level=" + level);
    }

    public void reset() {
        if (active()) throw new IllegalStateException("cannot reset active recovery");
        level = 0;
    }

    public int level() {
        return level;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (level < 1) return fail(FailureCode.FAULT, "recovery level is not set");
        return children().get(level - 1).execute(ctx);
    }
}
