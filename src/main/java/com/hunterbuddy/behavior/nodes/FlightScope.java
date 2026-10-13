package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.FlightBiomes;
import com.hunterbuddy.behavior.mc.FlightMeasurement;
import com.hunterbuddy.behavior.mc.FlightPlan;

public final class FlightScope extends Decorator {
    private final FlightPlan plan;
    private FlightMeasurement measurement;
    private FlightBiomes biomes;
    private boolean initialized;

    public FlightScope(FlightPlan plan, Node child) {
        super("measured-flight", child);
        this.plan = plan;
    }

    @Override
    protected void onStart(TickContext ctx) {
        initialized = false;
        String owner = ctx.owner();
        activation().defer(() -> plan.outputs.flight().releasePilot(owner));
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        try {
            if (!initialized) {
                plan.initialize(ctx);
                plan.outputs.flight().claim(ctx.owner(), plan::rocketReserve);
                plan.outputs.inventory().observePlayer();
                measurement = new FlightMeasurement(plan);
                measurement.reset(ctx);
                biomes = new FlightBiomes(plan.world, ctx);
                initialized = true;
            }
            if (!plan.worldKey.equals(plan.world.storageKey())) {
                return fail(FailureCode.REFUSED, "flight belongs to another world or dimension");
            }
            measurement.sample(ctx);
            biomes.sample(ctx);
            plan.observer.accept(ctx);
            if (!plan.returning && ctx.sensors().gliding() && plan.totalDurability() <= plan.returnWear() + 16) {
                String reason = "untouchable return elytra durability reserve reached";
                rememberFailure(ctx, reason);
                return fail(FailureCode.BUDGET, reason);
            }
            NodeResult result = child().execute(ctx);
            if (result instanceof Failure failure) rememberFailure(ctx, failure.reason());
            return result;
        } catch (OperationFailure ex) {
            rememberFailure(ctx, ex.getMessage());
            return fail(ex.code(), ex.getMessage());
        } catch (IllegalStateException ex) {
            rememberFailure(ctx, ex.getMessage());
            return fail(FailureCode.FAULT, ex.getMessage());
        }
    }

    private void rememberFailure(TickContext ctx, String reason) {
        ctx.bb().set(ctx.owner(), FlightPlan.LAST_FAILURE, reason);
        ctx.checkpoint(ctx.owner());
    }

    @Override
    protected void onResume(TickContext ctx, long ticks, long ms) {
        if (initialized && plan.world.available()) measurement.reset(ctx);
        if (initialized && !plan.outputs.flight().ownsPilot(ctx.owner())) {
            plan.outputs.flight().claim(ctx.owner(), plan::rocketReserve);
        }
        plan.improvedAt += ticks;
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        if (initialized && plan.world.available() && plan.worldKey.equals(plan.world.storageKey())) {
            measurement.sample(ctx);
            plan.performance.save(ctx);
            biomes.save(ctx);
        }
    }
}
