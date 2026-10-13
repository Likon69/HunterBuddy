package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public final class MineReturnTree {
    private MineReturnTree() {}

    public static Node build(MineSiteContext mine) {
        return new Guard(new Condition("mine access belongs to this world", sensors -> mine.facts == null
            || mine.facts.world.equals(mine.world.storageKey())), new Sequence("return-through-confirmed-galleries-and-stairs",
            new InventoryMilestone("load-confirmed-return-steps", ctx -> {
                mine.returnSteps.clear();
                if (mine.facts == null) return;
                List<Long> positions = new ArrayList<>(mine.facts.stairs);
                Collections.reverse(positions);
                for (Long position : positions) mine.returnSteps.add(BlockPos.fromLong(position));
                if (mine.returnSteps.isEmpty()) mine.returnSteps.add(BlockPos.fromLong(mine.facts.entrance));
            }),
            new RepeatUntil("follow-carved-access-back", () -> mine.returnSteps.isEmpty(), 2400,
                new Sequence("native-return-step",
                    new Deferred("reach-known-mine-step", () -> GroundTravel.build(mine.returnSteps.getFirst(), 0,
                        mine.baritone, mine.outputs)).resumable(),
                    new InventoryMilestone("confirm-return-step", ctx -> {
                        BlockPos point = mine.returnSteps.removeFirst();
                        ctx.progress("mine return step reached=" + point.toShortString());
                    })).resumable())).resumable());
    }
}
