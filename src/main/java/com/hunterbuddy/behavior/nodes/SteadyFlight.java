package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.FlightTerrain;
import com.hunterbuddy.behavior.resource.MovementIntent;

public final class SteadyFlight extends Decorator {
    private final FlightPlan plan;

    public SteadyFlight(FlightPlan plan, Node child) {
        super("steady-flight-during-equipment", child);
        this.plan = plan;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (ctx.sensors().gliding() && !plan.nativeMovement()) {
            float yaw = FlightTerrain.heading(plan.world, ctx.sensors(), ctx.sensors().yaw());
            plan.outputs.look(ctx.owner(), FlightTerrain.rotation(ctx.sensors(), yaw, -10));
            plan.outputs.movement(ctx.owner(), new MovementIntent(false, false, false, false, false, false, false));
        }
        return child().execute(ctx);
    }
}
