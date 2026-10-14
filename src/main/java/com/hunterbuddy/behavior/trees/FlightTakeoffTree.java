package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.OperationFailure;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.FlightTerrain;
import com.hunterbuddy.behavior.nodes.AfterEating;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.FireRocket;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.If;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RecordFailedTakeoff;
import com.hunterbuddy.behavior.nodes.RedeployGlide;
import com.hunterbuddy.behavior.nodes.SelectSlot;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.SteadyFlight;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WaitUntil;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.probes.BlockScan;
import com.hunterbuddy.behavior.probes.FindTakeoffPoints;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.util.Rockets;
import java.util.Comparator;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

public final class FlightTakeoffTree {
    private static final Key<BlockPos> HERE = new Key<>("flight-launch-origin", BlockPos.class, Scope.OWNER, false);
    private static final Key<BlockScan> POINTS = new Key<>("flight-launch-points", BlockScan.class, Scope.OWNER, false);
    private static final Key<BlockPos> CHOSEN = new Key<>("flight-launch-chosen", BlockPos.class, Scope.OWNER, false);
    private static final Key<Integer> ROCKET = new Key<>("redeploy-rocket", Integer.class, Scope.OWNER, false);
    private FlightTakeoffTree() {}

    public static Node build(FlightPlan plan) {
        return new Sequence("recover-glide-or-launch",
            new InventoryMilestone("check-new-dry-launch-fuel", ctx -> {
                if (ctx.sensors().onGround() && !ctx.sensors().gliding() && !ctx.sensors().inWater()
                    && !ctx.sensors().submerged() && !plan.outputs.flight().rocketAvailable()) {
                    throw new OperationFailure(FailureCode.BUDGET, "no plain rockets above the reserve for a new launch");
                }
            }),
            new Selector("takeoff-or-next-point",
            new If(new Condition("airborne outside water", sensors -> !sensors.onGround()
                && !sensors.inWater() && !sensors.submerged()),
                new Sequence("airborne-redeployment-before-any-ground-search",
                    new Timeout(200, new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT,
                        new RedeployGlide(plan.world, plan.outputs))),
                    LogisticsOperations.unless("redeployment needs no boost", () -> !plan.world.client().player.isGliding()
                        || plan.outputs.flight().landing() || plan.nearDestination()
                        || Math.hypot(plan.world.client().player.getVelocity().x, plan.world.client().player.getVelocity().z) >= 1.2,
                        boost(plan)))),
            new Guard(new Condition("already gliding", sensors -> sensors.gliding()), new Succeed()),
            new If(new Condition("water escape", sensors -> sensors.inWater() || sensors.submerged()),
                ExitWaterTree.build(plan.world, plan.baritone, plan.outputs)),
            new If(new Condition("clear dry launch", sensors -> sensors.onGround()
                && !plan.failedTakeoffs.contains(plan.world.feet()) && FlightTerrain.launch(plan.world.feet(), plan.world)),
                new RecordFailedTakeoff(plan, TakeoffTree.build(plan.world, plan.outputs))),
            new If(new Condition("supported launch search", sensors -> sensors.onGround()),
            new Sequence("find-another-launch-point",
                new SetValue<>(HERE, ctx -> plan.world.feet()),
                new Timeout(2400, new FindTakeoffPoints(HERE, POINTS, plan.world,
                    pos -> FlightTerrain.launch(pos, plan.world) && !plan.failedTakeoffs.contains(pos))),
                new InventoryMilestone("choose-dry-launch", ctx -> {
                    BlockPos chosen = ctx.bb().get(ctx.owner(), POINTS).orElseThrow().blocks().stream()
                        .filter(pos -> FlightTerrain.launch(pos, plan.world) && !plan.failedTakeoffs.contains(pos))
                        .min(Comparator.comparingDouble(plan::distance)).orElse(null);
                    if (chosen == null) ctx.bb().remove(ctx.owner(), CHOSEN);
                    else ctx.bb().set(ctx.owner(), CHOSEN, chosen);
                }),
                new Deferred("travel-or-prepare-a-supported-launch", () -> new Selector("loaded-or-prepared-launch",
                    new Sequence("travel-to-loaded-launch", new TravelToFlightLaunch(plan, CHOSEN),
                        new WaitUntil(new Condition("Baritone finished before takeoff", sensors -> !plan.nativeMovement()), 100),
                        new RecordFailedTakeoff(plan, TakeoffTree.build(plan.world, plan.outputs))),
                    FlightLaunchPreparationTree.build(plan)))))));
    }

    private static Node boost(FlightPlan plan) {
        Node rocket = new Timeout(100, new WithLease(Set.of(Resource.SLOT, Resource.ROCKETS, Resource.INTERACT), LeasePolicy.WAIT,
            new Sequence("boost-after-redeployment", new SetValue<>(ROCKET, ctx -> plan.world.findSlot(Rockets::isPlain)),
                new SelectSlot(Rockets::isPlain, true, plan.world, plan.outputs), new FireRocket(ROCKET, plan.outputs))));
        return new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT,
            new SteadyFlight(plan, new AfterEating(rocket)));
    }
}
