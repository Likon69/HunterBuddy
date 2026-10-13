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
        if (!world.available()) return fail(FailureCode.UNREACHABLE, "Minecraft world is unavailable");
        BlockPos feet = world.feet();
        if (!sensors.submerged()) return Success.INSTANCE;
        LookIntent look = new LookIntent(sensors.yaw(), -90);
        for (var direction : net.minecraft.util.math.Direction.Type.HORIZONTAL) {
            BlockPos next = feet.offset(direction);
            if (world.loaded(next) && world.state(next).isAir() && world.state(next.up()).isAir()) {
                LookIntent desired = com.hunterbuddy.behavior.mc.Conditions.towards(world.eyes(),
                    new com.hunterbuddy.behavior.core.TargetPoint(next.getX() + 0.5, next.getY() + 1.5, next.getZ() + 0.5));
                look = new LookIntent(sensors.yaw() + com.hunterbuddy.behavior.mc.Conditions.wrap(desired.yaw() - sensors.yaw()), desired.pitch());
                break;
            }
        }
        outputs.look(ctx.owner(), look);
        outputs.movement(ctx.owner(), new MovementIntent(true, false, false, false, true, false, sensors.food() > 6));
        if (sensors.y() - bestY >= 0.5 && ctx.progress(new ProgressEvidence(EvidenceKind.GOAL_BEST, sensors.y() - bestY, "swim Y best"))) bestY = sensors.y();
        return new Running("swimming up column=" + column);
    }
}
