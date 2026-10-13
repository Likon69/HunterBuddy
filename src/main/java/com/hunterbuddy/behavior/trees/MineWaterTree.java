package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.FindMineBreathingPoint;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.SwimUp;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WaitUntil;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;

public final class MineWaterTree {
    private MineWaterTree() {}

    public static Node build(MineSiteContext mine) {
        return new Sequence("recover-air-and-resume-mining",
            new Selector("native-air-pocket-or-existing-SwimUp",
                new Sequence("native-travel-to-known-air", new FindMineBreathingPoint(mine),
                    new Deferred("reach-air-with-Baritone", () -> GroundTravel.build(mine.breathingPoint, 0,
                        mine.baritone, mine.outputs))),
                new Sequence("existing-water-ascent", new WaitUntil(new Condition("native movement finished", sensors -> {
                    var nativeMove = mine.baritone.capture();
                    return !nativeMove.inControl() && !nativeMove.pathing() && !nativeMove.calcInProgress();
                }), 100), new SetValue<>(mine.swimKey, ctx -> mine.world.feet()),
                    new Timeout(200, new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT,
                        new SwimUp(mine.swimKey, mine.world, mine.outputs))))),
            new WaitUntil(new Condition("air recovered", sensors -> sensors.air() >= 280), 160));
    }
}
