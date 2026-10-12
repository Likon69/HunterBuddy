package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.MovementIntent;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.Objects;

public final class KeepFlying extends Action {
    private final Outputs outputs;

    public KeepFlying(Outputs outputs) {
        super("keep-flying");
        this.outputs = Objects.requireNonNull(outputs);
        limit(12_000);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (!ctx.sensors().gliding()) return Success.INSTANCE;
        outputs.look(ctx.owner(), new LookIntent(ctx.sensors().yaw(), 0));
        outputs.movement(ctx.owner(), new MovementIntent(true, false, false, false, false, false, false));
        return new Running("Landing: keeping flat flight without rockets");
    }
}
