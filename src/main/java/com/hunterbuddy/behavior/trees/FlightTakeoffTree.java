package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.FlightTerrain;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WaitUntil;
import com.hunterbuddy.behavior.probes.BlockScan;
import com.hunterbuddy.behavior.probes.FindTakeoffPoints;
import java.util.Comparator;
import net.minecraft.util.math.BlockPos;

public final class FlightTakeoffTree {
    private static final Key<BlockPos> HERE = new Key<>("flight-launch-origin", BlockPos.class, Scope.OWNER, false);
    private static final Key<BlockScan> POINTS = new Key<>("flight-launch-points", BlockScan.class, Scope.OWNER, false);
    private static final Key<BlockPos> CHOSEN = new Key<>("flight-launch-chosen", BlockPos.class, Scope.OWNER, false);
    private FlightTakeoffTree() {}

    public static Node build(FlightPlan plan) {
        return new Selector("takeoff-or-next-point",
            new Guard(new Condition("already gliding", sensors -> sensors.gliding()), new Succeed()),
            new Guard(new Condition("water escape", sensors -> sensors.inWater() || sensors.submerged()),
                ExitWaterTree.build(plan.world, plan.baritone, plan.outputs)),
            new Guard(new Condition("clear dry launch", sensors -> sensors.onGround()
                && FlightTerrain.launch(plan.world.feet(), plan.world)), TakeoffTree.build(plan.world, plan.outputs)),
            new Sequence("find-another-launch-point",
                new SetValue<>(HERE, ctx -> plan.world.feet()),
                new Timeout(2400, new FindTakeoffPoints(HERE, POINTS, plan.world)),
                new InventoryMilestone("choose-dry-launch", ctx -> {
                    BlockPos chosen = ctx.bb().get(ctx.owner(), POINTS).orElseThrow().blocks().stream()
                        .filter(pos -> FlightTerrain.launch(pos, plan.world))
                        .min(Comparator.comparingDouble(plan::distance)).orElseThrow(() ->
                            new IllegalStateException("no loaded launch point: return or choose another destination"));
                    ctx.bb().set(ctx.owner(), CHOSEN, chosen);
                }),
                new TravelToFlightLaunch(plan, CHOSEN),
                new WaitUntil(new Condition("Baritone finished before takeoff", sensors -> !plan.nativeMovement()), 100),
                TakeoffTree.build(plan.world, plan.outputs)));
    }
}
