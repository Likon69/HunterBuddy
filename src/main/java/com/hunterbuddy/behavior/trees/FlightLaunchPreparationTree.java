package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.FlightPlan;
import com.hunterbuddy.behavior.mc.InventoryLayout;
import com.hunterbuddy.behavior.nodes.ClearLaunchHeadroom;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.FindLaunchPreparation;
import com.hunterbuddy.behavior.nodes.PillarUp;
import com.hunterbuddy.behavior.nodes.RecordFailedTakeoff;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WaitUntil;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

public final class FlightLaunchPreparationTree {
    private static final Key<BlockPos> POINT = new Key<>("prepared-launch-point", BlockPos.class, Scope.OWNER, false);
    private static final Key<Integer> HEIGHT = new Key<>("prepared-launch-height", Integer.class, Scope.OWNER, false);

    private FlightLaunchPreparationTree() {}

    public static Node build(FlightPlan plan) {
        return new Selector("pillar-or-authorized-air-clearance", prepare(plan, true), prepare(plan, false));
    }

    private static Node prepare(FlightPlan plan, boolean pillar) {
        return new Sequence(pillar ? "build-launch-pillar" : "clear-launch-headroom",
            new Timeout(2400, new FindLaunchPreparation(plan, POINT, HEIGHT, pillar)),
            new TravelToFlightLaunch(plan, POINT),
            new WaitUntil(new Condition("native launch travel finished", sensors -> !plan.nativeMovement()), 100),
            new Deferred("prepare-supported-air-column", () -> pillar
                ? new Sequence("prepare-disposable-block-and-rise",
                    EquipmentTree.prepareHeld(new InventoryLayout(plan.world), plan.outputs.placement()::pillarItem,
                        5, plan.outputs, new MissionJournal(8)),
                    new Timeout(1800, new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK, Resource.SLOT, Resource.INTERACT),
                        LeasePolicy.WAIT, new PillarUp(ctx -> ctx.bb().get(ctx.owner(), HEIGHT).orElseThrow(), plan.world, plan.outputs))))
                : new Timeout(2400, new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK, Resource.MINING, Resource.SLOT),
                    LeasePolicy.WAIT, new ClearLaunchHeadroom(plan)))),
            new RecordFailedTakeoff(plan, TakeoffTree.build(plan.world, plan.outputs)));
    }
}
