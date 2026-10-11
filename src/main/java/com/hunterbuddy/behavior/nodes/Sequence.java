package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;

public final class Sequence extends Composite {
    private int index;
    private boolean resumable;
    private boolean resume;

    public Sequence(String name, Node... children) {
        super(name, children);
    }

    public Sequence resumable() {
        if (active()) throw new IllegalStateException("cannot configure an active Sequence");
        resumable = true;
        return this;
    }

    @Override
    protected void onStart(TickContext ctx) {
        if (!resume) index = 0;
        resume = false;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        while (index < children().size()) {
            NodeResult result = children().get(index).execute(ctx);
            if (result instanceof Running) return result;
            if (result instanceof Failure failure) return fail(failure.code(), name() + ": " + failure.reason());
            index++;
        }
        return Success.INSTANCE;
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        resume = resumable && (reason == ExitReason.PREEMPTED || reason == ExitReason.PAUSED);
        if (!resume) index = 0;
    }

    @Override
    protected void onReset() {
        index = 0;
        resume = false;
    }
}
