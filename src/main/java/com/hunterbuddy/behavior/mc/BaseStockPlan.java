package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.util.Rockets;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class BaseStockPlan {
    private BaseStockPlan() {}

    public static void measure(TickContext ctx, BaseVisitContext visit) {
        FlightPerformance performance = FlightPerformance.load(ctx);
        FarmerZone zone = visit.logistics.facts.activeZone();
        BlockPos departure = visit.position(visit.facts.departure);
        double x = departure.getX();
        double z = departure.getZ();
        if (!zone.contains(x + 0.5, z + 0.5)) {
            x = zone.corners().stream().mapToInt(BlockPos::getX).average().orElseThrow();
            z = zone.corners().stream().mapToInt(BlockPos::getZ).average().orElseThrow();
        }
        BlockPos base = visit.position(visit.facts.delivery);
        double distance = Math.hypot(x - base.getX(), z - base.getZ());
        for (BlockPos corner : zone.corners()) {
            distance = Math.max(distance, Math.hypot(corner.getX() - base.getX(), corner.getZ() - base.getZ()));
        }
        if (distance > visit.settings.flightRadius()) {
            throw new IllegalStateException("zone outside configured flight radius");
        }
        FlightBudget budget = new FlightBudget(performance, visit.logistics.outputs.flight().minimumRockets());
        visit.facts.rocketBudget = budget.departureRockets(distance);
        visit.facts.wearBudget = budget.departureWear(distance);
        visit.logistics.requiredFlightWear = visit.facts.wearBudget;
        visit.save(ctx, "measured budget: " + visit.facts.rocketBudget + " rockets and " + visit.facts.wearBudget + " durability");
    }

    public static void empties(TickContext ctx, BaseVisitContext visit) {
        List<ItemStack> contents = visit.enderContents();
        int partials = 0;
        int other = 0;
        for (int i = 0; i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            if (stack.isEmpty() || visit.roleSlot(i, LogisticsRoles.RESERVE) || visit.roleSlot(i, LogisticsRoles.MISCELLANEOUS)) continue;
            if (Rockets.isPlain(stack)) continue;
            if (ShulkerSignature.isShulker(stack)) {
                ShulkerSignature signature = ShulkerSignature.of(stack, visit.logistics.world);
                if (LogisticsInventory.pure(signature, visit.logistics.profile)) {
                    if (!signature.empty()) partials++;
                    continue;
                }
            }
            other++;
        }
        long remaining = Math.max(0, visit.facts.target - visit.logistics.mission.delivered(ctx, visit.facts.step) - partials);
        long rocketSlots = contents.stream().filter(Rockets::isPlain).count();
        int capacity = Math.max(0, 27 - 2 - (int) rocketSlots - partials - other);
        visit.facts.emptyTarget = (int) Math.min(remaining, capacity);
        visit.save(ctx, "ender chest plan: " + visit.facts.emptyTarget + " useful empties, reserve, miscellaneous and fuel");
    }
}
