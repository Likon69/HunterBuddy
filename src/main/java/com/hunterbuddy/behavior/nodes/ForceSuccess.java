package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Set;

public final class ForceSuccess extends Decorator {
    private Set<FailureCode> codes;

    public ForceSuccess(Node child) {
        super("force-success", child);
    }

    public ForceSuccess only(FailureCode... codes) {
        if (active()) throw new IllegalStateException("cannot configure an active ForceSuccess");
        this.codes = Set.of(codes);
        return this;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        if (result instanceof Running) return result;
        if (result instanceof Failure failure && codes != null && !codes.contains(failure.code())) return failure;
        return Success.INSTANCE;
    }
}
