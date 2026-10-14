package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressEvidence;
import com.hunterbuddy.behavior.core.RocketAction;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.Conditions;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.FlightTerrain;
import com.hunterbuddy.behavior.resource.MovementIntent;
import com.hunterbuddy.behavior.resource.SlotIntent;
import com.hunterbuddy.util.GliderSwap;
import com.hunterbuddy.util.Rockets;

public final class Cruise extends RocketAction {
    private final FlightPlan plan;
    private long firedAt = -100;
    private int beforeRocket;
    private boolean rocketPending;
    private boolean climbing;

    public Cruise(FlightPlan plan) {
        super("cruise");
        this.plan = plan;
        limit(72000);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        if (!Double.isFinite(plan.bestDistance)) plan.bestDistance = plan.distance(plan.destination);
        climbing = ctx.sensors().y() < plan.altitude;
        rocketPending = false;
        firedAt = ctx.tick();
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (plan.nearDestination() || plan.maintenanceNeeded()) return Success.INSTANCE;
        if (plan.nativeMovement()) return new Running("waiting for Baritone to finish; no manual takeover");
        var sensors = ctx.sensors();
        double distance = plan.distance(plan.destination);
        if (distance < plan.bestDistance - 2 && ctx.progress(new ProgressEvidence(
            EvidenceKind.GOAL_BEST, plan.bestDistance - distance, "flight destination closer"))) {
            plan.bestDistance = distance;
            plan.improvedAt = ctx.tick();
        }
        if (ctx.tick() - plan.improvedAt > 600) return fail(FailureCode.NO_PROGRESS, "flight corridor made no progress for 30 seconds");
        double dx = plan.destination.getX() + 0.5 - sensors.x();
        double dz = plan.destination.getZ() + 0.5 - sensors.z();
        float direct = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yaw = FlightTerrain.heading(plan.world, sensors, direct);
        double desired = FlightTerrain.clearance(plan, sensors, yaw);
        boolean obstacle = desired > sensors.y() + 12;
        if (sensors.y() <= desired - 18) climbing = true;
        if (sensors.y() >= desired + 18) climbing = false;
        float pitch = obstacle ? -40 : climbing ? -25 : 8;
        if (plan.speed(sensors) < 0.6 && sensors.y() > desired - 12) pitch = 25;
        plan.outputs.look(ctx.owner(), FlightTerrain.rotation(sensors, yaw, pitch));
        plan.outputs.movement(ctx.owner(), new MovementIntent(false, false, false, false, false, false, false));
        if (sensors.eating()) return new Running("cruising while waiting for the meal to finish before boosting");
        int count = plan.world.countMatching(Rockets::isPlain);
        if (rocketPending) {
            if (plan.outputs.inventory().confirmedTotal(Rockets::isPlain) < beforeRocket
                || plan.world.client().player.isCreative() && GliderSwap.rocketAttached(plan.world.client().player)) rocketPending = false;
            else if (ctx.tick() - firedAt > 60) return fail(FailureCode.NOT_CONFIRMED, "cruise rocket consumption unconfirmed");
        }
        if (count <= plan.rocketReserve() + (plan.returning ? 0 : 2)) return fail(FailureCode.BUDGET, "untouchable return rocket reserve reached");
        boolean aligned = Math.abs(Conditions.wrap(yaw - sensors.yaw())) < 20;
        boolean losing = plan.speed(sensors) < 1.2 || sensors.y() < desired - 24
            || climbing && sensors.velocityY() < -0.05 && sensors.y() < desired - 8;
        if (!rocketPending && losing && aligned && ctx.tick() - firedAt >= 40
            && !GliderSwap.rocketAttached(plan.world.client().player)) {
            int slot = plan.world.findSlot(Rockets::isPlain);
            plan.outputs.slot(ctx.owner(), new SlotIntent(slot, true));
            if (sensors.selectedSlot() == slot && sensors.serverSlot() == slot && plan.outputs.slotSettled(slot)
                && Math.abs(sensors.pitch() - pitch) < 8 && plan.outputs.flight().rocket(ctx, slot)) {
                beforeRocket = count;
                firedAt = ctx.tick();
                rocketPending = true;
            }
        }
        return new Running("cruise distance=" + (int) distance + " return rockets=" + plan.rocketReserve());
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        firedAt += ticks;
    }
}
