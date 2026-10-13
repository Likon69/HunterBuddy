package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.FlightTerrain;

public final class ConfirmFlightArrival extends Action {
    private final FlightPlan plan;
    private int stable;

    public ConfirmFlightArrival(FlightPlan plan) {
        super("confirm-dry-flight-arrival");
        this.plan = plan;
        limit(100);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        stable = 0;
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (ctx.sensors().gliding() || !ctx.sensors().onGround() && !ctx.sensors().inWater()) {
            stable = 0;
            return new Running("waiting for grounded landing evidence");
        }
        if (ctx.sensors().inWater() || plan.landing == null || plan.distance(plan.landing) > 12
            || !FlightTerrain.landing(plan.world.feet(), plan.world)) {
            return fail(FailureCode.UNREACHABLE, "landing missed the selected dry flat point; choose the next candidate");
        }
        if (!ctx.monitor().samplesValid(ctx.tick())) stable = 0;
        else stable++;
        if (stable < 10) return new Running("confirming stable dry arrival");
        ctx.checkpoint(ctx.owner());
        return Success.INSTANCE;
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        stable = 0;
    }
}
