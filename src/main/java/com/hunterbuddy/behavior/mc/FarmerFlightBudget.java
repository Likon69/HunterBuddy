package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.util.Rockets;
import net.minecraft.util.math.BlockPos;

public final class FarmerFlightBudget {
    private FarmerFlightBudget() {}

    public static void requestBase(TickContext ctx, FarmerMissionContext mission, String reason) {
        mission.facts.baseRequested = true;
        if (!mission.unfinishedMine()) mission.facts.siteActive = false;
        mission.save(ctx, reason);
    }

    public static boolean returnDue(TickContext ctx, BlockPos home, WorldReader world, Outputs outputs) {
        FlightPerformance performance = FlightPerformance.load(ctx);
        double distance = Math.hypot(world.feet().getX() - home.getX(), world.feet().getZ() - home.getZ());
        return new FlightBudget(performance, outputs.flight().minimumRockets()).returnDue(distance,
            world.countMatching(Rockets::isPlain), FlightPlan.totalDurability(world));
    }

    public static boolean canReach(TickContext ctx, BlockPos destination, BlockPos home, WorldReader world, Outputs outputs) {
        double outward = Math.hypot(world.feet().getX() - destination.getX(), world.feet().getZ() - destination.getZ());
        if (ctx.sensors().onGround() && outward <= 64) return true;
        double back = Math.hypot(destination.getX() - home.getX(), destination.getZ() - home.getZ());
        FlightPerformance performance = FlightPerformance.load(ctx);
        FlightBudget budget = new FlightBudget(performance, outputs.flight().minimumRockets());
        return world.countMatching(Rockets::isPlain) >= budget.tripRockets(outward, back, false)
            && FlightPlan.totalDurability(world) >= budget.tripWear(outward, back, false);
    }
}
