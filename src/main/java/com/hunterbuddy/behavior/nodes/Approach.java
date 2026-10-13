package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressEvidence;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.FlightTerrain;
import com.hunterbuddy.behavior.probes.BlockScan;
import com.hunterbuddy.behavior.probes.FindTakeoffPoints;
import com.hunterbuddy.behavior.resource.MovementIntent;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public final class Approach extends Action {
    private static final Key<BlockPos> ORIGIN = new Key<>("flight-landing-origin", BlockPos.class, Scope.OWNER, false);
    private static final Key<BlockScan> POINTS = new Key<>("flight-landing-points", BlockScan.class, Scope.OWNER, false);
    private final FlightPlan plan;
    private final Node probe;
    private double bestHeight;

    public Approach(FlightPlan plan) {
        super("approach-without-rockets");
        this.plan = plan;
        probe = new Timeout(2400, new Sequence("find-flat-dry-landing",
            new SetValue<>(ORIGIN, ctx -> plan.destination), new FindTakeoffPoints(ORIGIN, POINTS, plan.world)));
        limit(3600);
    }

    @Override
    public List<Node> children() {
        return List.of(probe);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        if (plan.landing != null) plan.failedLandings.add(plan.landing);
        plan.landing = null;
        bestHeight = Double.POSITIVE_INFINITY;
        probe.resetMemory();
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (ctx.sensors().onGround() || ctx.sensors().inWater()) return Success.INSTANCE;
        if (!ctx.sensors().gliding()) return fail(FailureCode.NOT_CONFIRMED, "glide lost during approach");
        if (plan.nativeMovement()) return new Running("waiting for Baritone; approach never cancels its movement");
        if (plan.landing != null && !FlightTerrain.landing(plan.landing, plan.world)) {
            plan.failedLandings.add(plan.landing);
            plan.landing = null;
            probe.halt(ctx, com.hunterbuddy.behavior.core.ExitReason.PREEMPTED);
            probe.resetMemory();
        }
        if (plan.landing == null) {
            NodeResult result = probe.execute(ctx);
            circle(ctx, plan.destination, 48, -3);
            if (result instanceof Running) return result;
            if (!(result instanceof Success)) return result;
            BlockPos exact = plan.world.surface(plan.destination.getX(), plan.destination.getZ());
            if (FlightTerrain.landing(exact, plan.world) && !plan.failedLandings.contains(exact)) plan.landing = exact;
            else plan.landing = ctx.bb().get(ctx.owner(), POINTS).orElseThrow().blocks().stream()
                .filter(pos -> FlightTerrain.landing(pos, plan.world) && !plan.failedLandings.contains(pos))
                .min(Comparator.comparingDouble(pos -> Math.hypot(pos.getX() - plan.destination.getX(),
                    pos.getZ() - plan.destination.getZ()))).orElse(null);
            if (plan.landing == null) return fail(FailureCode.NO_OPTION, "no dry flat loaded landing point near destination");
            return new Running("landing point selected=" + plan.landing.toShortString());
        }
        var sensors = ctx.sensors();
        double distance = plan.distance(plan.landing);
        double height = sensors.y() - plan.landing.getY();
        double speed = plan.speed(sensors);
        if (!Double.isFinite(bestHeight)) bestHeight = height;
        if (height < bestHeight - 2 && ctx.progress(new ProgressEvidence(EvidenceKind.GOAL_BEST,
            bestHeight - height, "approach height decreased"))) bestHeight = height;
        if (distance <= 8 && height <= 6 && speed < 0.9) return Success.INSTANCE;
        if (distance < 28 && height > 12) {
            circle(ctx, plan.landing, 24, speed > 2.2 ? 20 : 35);
        } else {
            double dx = plan.landing.getX() + 0.5 - sensors.x() - sensors.velocityX() * 4;
            double dz = plan.landing.getZ() + 0.5 - sensors.z() - sensors.velocityZ() * 4;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) Math.clamp(Math.toDegrees(Math.atan2(height - 4, Math.max(8, distance))), -25, 45);
            if (speed > 2.2) pitch -= 15;
            if (height < 8 && speed > 0.9) pitch = -25;
            if (!FlightTerrain.corridor(plan.world, sensors, yaw, Math.max(8, speed * 12))) {
                yaw = FlightTerrain.heading(plan.world, sensors, yaw);
                pitch = -15;
            }
            plan.outputs.look(ctx.owner(), FlightTerrain.rotation(sensors, yaw, pitch));
            plan.outputs.movement(ctx.owner(), new MovementIntent(false, false, false, false, false, false, false));
        }
        return new Running("approach distance=" + (int) distance + " height=" + (int) height + " rockets forbidden");
    }

    private void circle(TickContext ctx, BlockPos centre, double radius, float pitch) {
        var sensors = ctx.sensors();
        double dx = centre.getX() + 0.5 - sensors.x();
        double dz = centre.getZ() + 0.5 - sensors.z();
        float direct = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yaw = direct + 90 - (float) Math.clamp((Math.hypot(dx, dz) - radius) * 2, -35, 35);
        yaw = FlightTerrain.heading(plan.world, sensors, yaw);
        plan.outputs.look(ctx.owner(), FlightTerrain.rotation(sensors, yaw, pitch));
        plan.outputs.movement(ctx.owner(), new MovementIntent(false, false, false, false, false, false, false));
    }
}
