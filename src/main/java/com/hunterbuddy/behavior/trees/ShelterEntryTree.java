package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsInventory;
import com.hunterbuddy.behavior.mc.MineGeometry;
import com.hunterbuddy.behavior.nodes.PillarUp;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

public final class ShelterEntryTree {
    private ShelterEntryTree() {}

    public static Node build(LogisticsContext logistics) {
        BlockPos center = logistics.facts.origin();
        BlockPos feet = logistics.world.feet();
        if (Math.abs(feet.getX() - center.getX()) < 2 && Math.abs(feet.getZ() - center.getZ()) < 2
            || logistics.world.cleared(logistics.facts.door())) {
            return GroundTravel.withoutBreaking(center, logistics.baritone, logistics.outputs);
        }
        BlockPos approach = center.north(3);
        for (int height = 2; height >= 0; height--) {
            BlockPos candidate = center.north(3).up(height);
            if (MineGeometry.standing(logistics.world, candidate)) {
                approach = candidate;
                break;
            }
        }
        return new Sequence("walk-over-existing-shelter-wall",
            GroundTravel.withoutBreaking(approach, logistics.baritone, logistics.outputs),
            LogisticsOperations.unless("existing-shelter-wall-height-reached",
                () -> logistics.world.feet().getY() >= center.getY() + 2,
                new Sequence("reach-shelter-wall-height",
                    LogisticsOperations.prepare(logistics, LogisticsInventory::shelterBlock, 5),
                    new Timeout(400, new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK, Resource.SLOT, Resource.INTERACT), LeasePolicy.WAIT,
                        new PillarUp(center.getY() + 2, logistics.world, logistics.outputs, LogisticsInventory::shelterBlock))))),
            GroundTravel.withoutBreaking(center, logistics.baritone, logistics.outputs));
    }
}
