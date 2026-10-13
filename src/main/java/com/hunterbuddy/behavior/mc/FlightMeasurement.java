package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.util.GliderSwap;
import com.hunterbuddy.util.Rockets;
import net.minecraft.item.ItemStack;

public final class FlightMeasurement {
    private final FlightPlan plan;
    private Sensors previous;
    private long previousTick;
    private int rockets;
    private int wear;
    private int pairs;
    private long savedAt;

    public FlightMeasurement(FlightPlan plan) {
        this.plan = plan;
    }

    public void reset(TickContext ctx) {
        previous = ctx.sensors();
        previousTick = ctx.tick();
        rockets = plan.world.countMatching(Rockets::isPlain);
        wear = totalWear();
        pairs = plan.world.countMatching(GliderSwap::isGlider);
    }

    public void sample(TickContext ctx) {
        if (!plan.world.available() || !plan.worldKey.equals(plan.world.storageKey())) return;
        if (previous == null || !ctx.monitor().samplesValid(ctx.tick())) {
            reset(ctx);
            return;
        }
        Sensors now = ctx.sensors();
        double metres = Math.hypot(now.x() - previous.x(), now.z() - previous.z());
        long elapsed = ctx.tick() - previousTick;
        if (previous.gliding() && now.gliding() && elapsed > 0 && elapsed <= 2 && metres < 12) {
            plan.performance.metres += metres;
        }
        int currentRockets = plan.world.countMatching(Rockets::isPlain);
        int confirmedRockets = plan.outputs.inventory().confirmedTotal(Rockets::isPlain);
        if (currentRockets == confirmedRockets) {
            plan.performance.rockets += Math.max(0, rockets - currentRockets);
            rockets = currentRockets;
        }
        int currentPairs = plan.world.countMatching(GliderSwap::isGlider);
        int currentWear = totalWear();
        if (pairs == currentPairs && (previous.gliding() || now.gliding())) {
            plan.performance.durability += Math.max(0, currentWear - wear);
        }
        pairs = currentPairs;
        wear = currentWear;
        previous = now;
        previousTick = ctx.tick();
        if (ctx.tick() - savedAt >= 200) {
            plan.performance.save(ctx);
            savedAt = ctx.tick();
        }
    }

    private int totalWear() {
        int total = 0;
        for (int i = 0; i < plan.world.client().player.getInventory().size(); i++) {
            ItemStack stack = plan.world.client().player.getInventory().getStack(i);
            if (GliderSwap.isGlider(stack)) total += stack.getDamage();
        }
        return total;
    }
}
