package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.util.Rockets;
import net.minecraft.util.math.BlockPos;

public final class FarmerFlightBudget {
    private FarmerFlightBudget() {}

    public static boolean returnDue(TickContext ctx, BlockPos home, WorldReader world, Outputs outputs) {
        FlightPerformance performance = FlightPerformance.load(ctx);
        double distance = Math.hypot(world.feet().getX() - home.getX(), world.feet().getZ() - home.getZ());
        int flight = 2 + (int) Math.ceil(performance.rocketsPerKm() * distance / 1000);
        int reserve = Math.max(outputs.flight().minimumRockets(), 8 + (int) Math.ceil(flight * 1.5));
        int wear = 16 + (int) Math.ceil(performance.durabilityPerKm() * distance / 1000 * 1.5);
        return world.countMatching(Rockets::isPlain) <= reserve + 4 || FlightPlan.totalDurability(world) <= wear + 64;
    }

    public static boolean canReach(TickContext ctx, BlockPos destination, BlockPos home, WorldReader world, Outputs outputs) {
        double outward = Math.hypot(world.feet().getX() - destination.getX(), world.feet().getZ() - destination.getZ());
        if (ctx.sensors().onGround() && outward <= 64) return true;
        double back = Math.hypot(destination.getX() - home.getX(), destination.getZ() - home.getZ());
        FlightPerformance performance = FlightPerformance.load(ctx);
        int flight = 2 + (int) Math.ceil(performance.rocketsPerKm() * outward / 1000);
        int returning = 2 + (int) Math.ceil(performance.rocketsPerKm() * back / 1000);
        int reserve = Math.max(outputs.flight().minimumRockets(), 8 + (int) Math.ceil(returning * 1.5));
        int wear = 16 + (int) Math.ceil(performance.durabilityPerKm() * (outward + back) / 1000 * 1.5);
        return world.countMatching(Rockets::isPlain) >= (int) Math.ceil(flight * 1.5) + reserve
            && FlightPlan.totalDurability(world) >= wear;
    }
}
