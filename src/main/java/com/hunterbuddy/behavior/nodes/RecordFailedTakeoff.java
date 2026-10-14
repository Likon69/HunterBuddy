package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.FlightPlan;
import net.minecraft.util.math.BlockPos;

public final class RecordFailedTakeoff extends Decorator {
    private final FlightPlan plan;
    private BlockPos origin;

    public RecordFailedTakeoff(FlightPlan plan, Node child) {
        super("remember-failed-flight-launch", child);
        this.plan = plan;
    }

    @Override
    protected void onStart(TickContext ctx) {
        origin = plan.world.feet().toImmutable();
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        if (result instanceof Failure) plan.failedTakeoffs.add(origin);
        return result;
    }
}
