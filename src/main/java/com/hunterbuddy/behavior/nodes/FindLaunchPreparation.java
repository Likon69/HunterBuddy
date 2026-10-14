package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.MineGeometry;
import java.util.ArrayDeque;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class FindLaunchPreparation extends Action {
    private final FlightPlan plan;
    private final Key<BlockPos> point;
    private final Key<Integer> height;
    private final boolean pillar;
    private final ArrayDeque<BlockPos> candidates = new ArrayDeque<>();

    public FindLaunchPreparation(FlightPlan plan, Key<BlockPos> point, Key<Integer> height, boolean pillar) {
        super(pillar ? "find-supported-pillar-launch" : "find-authorized-launch-clearance");
        this.plan = plan;
        this.point = point;
        this.height = height;
        this.pillar = pillar;
        limit(2400);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        candidates.clear();
        BlockPos origin = plan.world.feet();
        candidates.add(origin);
        for (int radius = 1; radius <= 96; radius++) {
            for (int side = -radius; side <= radius; side++) {
                candidates.add(origin.add(side, 0, -radius));
                candidates.add(origin.add(side, 0, radius));
                if (Math.abs(side) < radius) {
                    candidates.add(origin.add(-radius, 0, side));
                    candidates.add(origin.add(radius, 0, side));
                }
            }
        }
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (!ctx.sensors().onGround()) return fail(FailureCode.NOT_CONFIRMED, "launch preparation requires ground support");
        if (pillar && plan.world.countMatching(plan.outputs.placement()::pillarItem) == 0) {
            return fail(FailureCode.NO_OPTION, "no disposable pillar blocks above their reserve");
        }
        for (int work = 0; work < 256 && !candidates.isEmpty(); work++) {
            BlockPos column = candidates.removeFirst();
            if (!plan.world.loaded(column)) continue;
            BlockPos feet = column.equals(plan.world.feet()) ? column : plan.world.surface(column.getX(), column.getZ());
            if (feet == null || !plan.world.inBounds(feet.up(6)) || !plan.world.loaded(feet)
                || !plan.world.state(feet.down()).isSideSolidFullSquare(plan.world.client().world, feet.down(), Direction.UP)
                || !MineGeometry.standing(plan.world, feet)
                || !plan.world.state(feet).getFluidState().isEmpty()
                || !plan.world.state(feet.up()).getFluidState().isEmpty() || MineGeometry.lavaAdjacent(plan.world, feet)) continue;
            int rise = pillar ? pillarHeight(feet) : clearable(feet) ? 0 : -1;
            if (rise < 0) continue;
            ctx.bb().set(ctx.owner(), point, feet);
            ctx.bb().set(ctx.owner(), height, feet.getY() + rise);
            return Success.INSTANCE;
        }
        return candidates.isEmpty() ? fail(FailureCode.NO_OPTION, "no safe loaded launch preparation within 96 blocks")
            : new Running("checking nearby supported launch preparation columns");
    }

    private int pillarHeight(BlockPos feet) {
        int available = plan.world.countMatching(plan.outputs.placement()::pillarItem);
        for (int rise = 1; rise <= Math.min(24, available); rise++) {
            for (int y = 2; y <= rise + 6; y++) {
                BlockPos head = feet.up(y);
                if (!plan.world.inBounds(head) || !plan.world.loaded(head) || !plan.world.state(head).isAir()) return -1;
            }
            if (!plan.failedTakeoffs.contains(feet.up(rise))) return rise;
        }
        return -1;
    }

    private boolean clearable(BlockPos feet) {
        var rule = plan.outputs.mining().breakRule();
        if (rule == null || !rule.test(feet) || plan.failedTakeoffs.contains(feet)) return false;
        for (int y = 2; y <= 6; y++) {
            BlockPos head = feet.up(y);
            if (!plan.world.inBounds(head) || !plan.world.loaded(head) || !rule.test(head)
                || MineGeometry.lavaAdjacent(plan.world, head) || MineGeometry.fallingAbove(plan.world, head)) return false;
            if (!plan.world.state(head).isAir() && (plan.world.hardness(head) < 0
                || plan.world.client().world.getBlockEntity(head) != null || plan.outputs.mining().protectedBlock(head))) return false;
        }
        return true;
    }
}
