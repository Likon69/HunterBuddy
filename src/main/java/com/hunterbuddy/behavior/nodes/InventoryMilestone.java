package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.function.Consumer;

public final class InventoryMilestone extends Node {
    private final Consumer<TickContext> operation;

    public InventoryMilestone(String name, Consumer<TickContext> operation) {
        super(name);
        this.operation = operation;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        try {
            operation.accept(ctx);
            return Success.INSTANCE;
        } catch (IllegalStateException | IllegalArgumentException ex) { return fail(FailureCode.REFUSED, ex.getMessage()); }
    }
}
