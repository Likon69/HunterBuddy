package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public final class LabTaskNode extends Decorator {
    private final Runnable completed;

    public LabTaskNode(Node child, Runnable completed) {
        super("lab-one-shot", child);
        this.completed = Objects.requireNonNull(completed);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        if (result instanceof Success) completed.run();
        else if (result instanceof Failure failure) ctx.stopBot(ExitReason.FAILED, "Lab failed [" + failure.code() + "]: " + failure.reason());
        return result;
    }
}
