package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.resource.MovementIntent;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.Objects;

public final class HoldKeys extends Action {
    private final MovementIntent intent;
    private final Outputs outputs;

    public HoldKeys(MovementIntent intent, Outputs outputs) {
        super("hold-keys");
        this.intent = Objects.requireNonNull(intent);
        this.outputs = Objects.requireNonNull(outputs);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (!ctx.sensors().baritoneAvailable()) return fail(FailureCode.UNSUPPORTED, "Baritone movement is unavailable");
        outputs.movement(ctx.owner(), intent);
        return Running.WORKING;
    }
}
