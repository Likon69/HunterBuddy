package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressEvidence;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.MovementIntent;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.Objects;
import net.minecraft.util.math.BlockPos;

public final class SwimUp extends Action {
    private final Key<BlockPos> columnKey;
    private final WorldReader world;
    private final Outputs outputs;
    private double bestY;

    public SwimUp(Key<BlockPos> columnKey, WorldReader world, Outputs outputs) {
        super("swim-up");
        this.columnKey = Objects.requireNonNull(columnKey);
        this.world = Objects.requireNonNull(world);
        this.outputs = Objects.requireNonNull(outputs);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        bestY = ctx.sensors().y();
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        BlockPos column = ctx.bb().get(ctx.owner(), columnKey).orElse(null);
        if (column == null) return fail(FailureCode.NO_OPTION, "swim column is missing");
        var sensors = ctx.sensors();
        if (!sensors.inWater() && !sensors.submerged()) return Success.INSTANCE;
        BlockPos feet = world.feet();
        if (feet.getX() != column.getX() || feet.getZ() != column.getZ() || world.openDryNeighbor(feet)) return Success.INSTANCE;
        outputs.look(ctx.owner(), new LookIntent(sensors.yaw(), -90));
        outputs.movement(ctx.owner(), new MovementIntent(true, false, false, false, true, false, sensors.food() > 6));
        if (sensors.y() - bestY >= 0.5 && ctx.progress(new ProgressEvidence(EvidenceKind.GOAL_BEST, sensors.y() - bestY, "swim Y best"))) bestY = sensors.y();
        return new Running("swimming up column=" + column);
    }
}
