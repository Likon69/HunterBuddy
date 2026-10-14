package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.Approach;
import com.hunterbuddy.behavior.nodes.ConfirmFlightArrival;
import com.hunterbuddy.behavior.nodes.Cruise;
import com.hunterbuddy.behavior.nodes.FlightScope;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.KeepFlying;
import com.hunterbuddy.behavior.nodes.Landing;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Retry;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.Touchdown;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.util.math.BlockPos;

public final class FlyToTree {
    private FlyToTree() {}

    public static Node build(BlockPos destination, WorldReader world, BaritoneBridge baritone, Outputs outputs) {
        return build(() -> destination, world::feet, false, world, baritone, outputs);
    }

    public static Node build(Supplier<BlockPos> destination, Supplier<BlockPos> home, boolean returning,
                             WorldReader world, BaritoneBridge baritone, Outputs outputs) {
        FlightPlan plan = new FlightPlan(destination, home, returning, world, baritone, outputs);
        return build(plan);
    }

    public static Node build(FlightPlan plan) {
        Node attempt = LogisticsOperations.unless("destination already reached", plan::atDestination, flight(plan));
        Node journey = new Retry(3, 0, attempt).on(FailureCode.NO_OPTION, FailureCode.UNREACHABLE,
            FailureCode.NOT_CONFIRMED, FailureCode.NO_PROGRESS);
        return new TaskScope(new FlightScope(plan, new Sequence("fly-to-destination",
            journey, FlightEquipmentTree.wear(plan, false))));
    }

    private static Node flight(FlightPlan plan) {
        return new Sequence("prepare-takeoff-cruise-land",
            new InventoryMilestone("new-flight-attempt", ctx -> {
                plan.improvedAt = ctx.tick();
                plan.bestDistance = Double.POSITIVE_INFINITY;
            }),
            FlightEquipmentTree.wear(plan, true), FlightEquipmentTree.rockets(plan), FlightTakeoffTree.build(plan),
            cruise(plan), FlightEquipmentTree.wear(plan, true), landing(plan),
            new Timeout(100, new ConfirmFlightArrival(plan)));
    }

    private static Node cruise(FlightPlan plan) {
        return new RepeatUntil("cruise-until-approach-range", plan::nearDestination, 72,
            new Sequence("flight-and-equipment-maintenance", FlightEquipmentTree.wear(plan, true),
                FlightEquipmentTree.rockets(plan), FlightTakeoffTree.build(plan),
                new Timeout(72000, new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK, Resource.ROCKETS),
                    LeasePolicy.WAIT, new Cruise(plan)))));
    }

    private static Node landing(FlightPlan plan) {
        return new Landing(new Sequence("approach-and-land-without-rockets",
            new Timeout(3600, new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT, new Approach(plan))),
            new Selector("landing-with-KeepFlying-fallback",
                new Timeout(400, new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT, new Touchdown(plan))),
                new Timeout(2400, new KeepFlying(plan.world, plan.baritone, plan.outputs, () -> plan.landing, point -> plan.landing = point)))));
    }
}
