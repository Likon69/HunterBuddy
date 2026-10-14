package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.FlightTerrain;
import com.hunterbuddy.behavior.resource.MovementIntent;
import java.util.List;

public final class Touchdown extends Action {
    private final FlightPlan plan;
    private final Node redeploy;

    public Touchdown(FlightPlan plan) {
        super("touchdown");
        this.plan = plan;
        redeploy = new Timeout(200, new RedeployGlide(plan.world, plan.outputs));
        limit(400);
    }

    @Override
    public List<Node> children() {
        return List.of(redeploy);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (!ctx.sensors().gliding() && (ctx.sensors().onGround() || ctx.sensors().inWater())) return Success.INSTANCE;
        if (!ctx.sensors().gliding()) {
            NodeResult result = redeploy.execute(ctx);
            return result instanceof Success ? new Running("glide redeployed during touchdown") : result;
        }
        if (plan.landing == null || !FlightTerrain.landing(plan.landing, plan.world)) {
            return fail(FailureCode.UNREACHABLE, "landing point changed; use KeepFlying fallback");
        }
        if (plan.nativeMovement()) return new Running("waiting for Baritone to release its flight controls");
        var sensors = ctx.sensors();
        double height = sensors.y() - plan.landing.getY();
        double dx = plan.landing.getX() + 0.5 - sensors.x() - sensors.velocityX() * 3;
        double dz = plan.landing.getZ() + 0.5 - sensors.z() - sensors.velocityZ() * 3;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.clamp(Math.toDegrees(Math.atan2(height, Math.hypot(dx, dz) + 8)), 8, 35);
        if (height < 2.5 && plan.speed(sensors) > 0.65) pitch = -12;
        plan.outputs.look(ctx.owner(), FlightTerrain.rotation(sensors, yaw, pitch));
        plan.outputs.movement(ctx.owner(), new MovementIntent(false, false, false, false, false, false, false));
        return new Running("landing flare without rockets");
    }
}
