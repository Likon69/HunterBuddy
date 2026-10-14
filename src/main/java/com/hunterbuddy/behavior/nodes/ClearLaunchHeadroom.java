package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.MineGeometry;
import com.hunterbuddy.behavior.resource.MovementIntent;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public final class ClearLaunchHeadroom extends Action {
    private static final Key<BlockPos> BLOCK = new Key<>("launch-headroom-block", BlockPos.class, Scope.OWNER, false);
    private final FlightPlan plan;
    private final Node mining;
    private BlockPos base;
    private int height;

    public ClearLaunchHeadroom(FlightPlan plan) {
        super("clear-authorized-launch-column-with-jump-reach");
        this.plan = plan;
        mining = new Timeout(300, new BreakBlock(BLOCK, true, plan.world, plan.outputs));
        limit(2400);
    }

    @Override
    public List<Node> children() {
        return List.of(mining);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        base = plan.world.feet();
        height = 2;
        mining.resetMemory();
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (plan.nativeMovement()) return new Running("waiting for native movement before clearing launch air");
        if (plan.world.feet().getX() != base.getX() || plan.world.feet().getZ() != base.getZ()
            || !MineGeometry.floor(plan.world, base.down())) return fail(FailureCode.UNREACHABLE, "launch support changed");
        while (height <= 6 && plan.world.state(base.up(height)).isAir()) {
            mining.halt(ctx, ExitReason.COMPLETED);
            mining.resetMemory();
            height++;
        }
        if (height > 6) return ctx.sensors().onGround() ? Success.INSTANCE
            : new Running("waiting to land on launch support after clearing");
        BlockPos point = base.up(height);
        var rule = plan.outputs.mining().breakRule();
        if (rule == null || !rule.test(base) || !rule.test(point)
            || MineGeometry.lavaAdjacent(plan.world, point) || MineGeometry.fallingAbove(plan.world, point)
            || plan.outputs.mining().protectedBlock(point) || plan.world.client().world.getBlockEntity(point) != null) {
            return fail(FailureCode.REFUSED, "launch air cannot be safely cleared inside the zone");
        }
        ctx.bb().set(ctx.owner(), BLOCK, point);
        boolean jump = point.getY() + 0.5 - (base.getY() + plan.world.client().player.getStandingEyeHeight()) > 4.5;
        plan.outputs.movement(ctx.owner(), new MovementIntent(false, false, false, false, jump, false, false));
        double reach = Math.sqrt(point.getSquaredDistance(plan.world.client().player.getEyePos()));
        if (reach > 4.5) return new Running("jumping into reach of the highest launch obstruction");
        NodeResult result = mining.execute(ctx);
        if (result instanceof Failure failure && failure.code() == FailureCode.UNREACHABLE && jump) {
            mining.resetMemory();
            return new Running("retrying launch obstruction from jump reach");
        }
        if (result instanceof Success) {
            mining.resetMemory();
            height++;
            return new Running("launch obstruction cleared");
        }
        return result;
    }
}
