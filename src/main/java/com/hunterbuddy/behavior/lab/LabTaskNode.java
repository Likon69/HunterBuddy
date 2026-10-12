package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public final class LabTaskNode extends Decorator {
    private final Runnable completed;
    private final Runnable unreachable;

    public LabTaskNode(Node child, Runnable completed, Runnable unreachable) {
        super("lab-one-shot", child);
        this.completed = Objects.requireNonNull(completed);
        this.unreachable = unreachable;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        if (result instanceof Success) completed.run();
        else if (result instanceof Failure failure) {
            if (unreachable != null) {
                unreachable.run();
                return fail(FailureCode.UNREACHABLE, "Lab destination exhausted [" + failure.code() + "]: " + failure.reason());
            }
            ctx.stopBot(ExitReason.FAILED, "Lab failed [" + failure.code() + "]: " + failure.reason());
        }
        return result;
    }
}
