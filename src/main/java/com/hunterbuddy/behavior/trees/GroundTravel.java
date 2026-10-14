package com.hunterbuddy.behavior.trees;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.nodes.BaritoneGoTo;
import com.hunterbuddy.behavior.nodes.Fail;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public final class GroundTravel {
    private GroundTravel() {}
    public static Node build(BlockPos pos, int radius, BaritoneBridge bridge, Outputs outputs) {
        if (pos == null) return new Fail(FailureCode.UNREACHABLE, "site has no loaded dry access inside the zone");
        return goal(radius == 0 ? new GoalBlock(pos) : new GoalNear(pos, radius), pos, bridge, outputs, true);
    }

    public static Node horizontal(BlockPos pos, BaritoneBridge bridge, Outputs outputs) {
        return goal(new GoalXZ(pos.getX(), pos.getZ()), pos, bridge, outputs, true);
    }

    public static Node withoutBreaking(BlockPos pos, BaritoneBridge bridge, Outputs outputs) {
        return goal(new GoalBlock(pos), pos, bridge, outputs, false);
    }

    private static Node goal(Goal goal, BlockPos destination, BaritoneBridge bridge, Outputs outputs, boolean allowBreak) {
        Key<Goal> key = new Key<>("logistics-ground-goal", Goal.class, Scope.OWNER, false);
        return new TaskScope(new Timeout(2_400, new Sequence("ground-logistics", new SetValue<>(key, ctx -> goal),
            new WithLease(Set.of(Resource.BARITONE, Resource.MINING), LeasePolicy.WAIT, new BaritoneGoTo(key, () -> {
                var rule = outputs.mining().breakRule();
                return allowBreak && rule != null && rule.test(MinecraftClient.getInstance().player.getBlockPos()) && rule.test(destination);
            }, false, bridge, outputs)))));
    }
}
