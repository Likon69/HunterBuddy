package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.Budget;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.ResetBudget;
import com.hunterbuddy.behavior.nodes.SelectSlot;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.TakeoffElytra;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WaitUntil;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.util.Rockets;
import java.util.Set;

public final class TakeoffTree {
    private static final Key<Integer> ROCKET = new Key<>("takeoff-rocket-slot", Integer.class, Scope.OWNER, false);
    private TakeoffTree() { }

    public static Node build(WorldReader world, Outputs outputs) {
        return new Sequence("takeoff-with-failure-budget", new Budget("takeoff", 2,
            new Timeout(600, new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK, Resource.ROCKETS), LeasePolicy.WAIT,
            new Sequence("takeoff", new WaitUntil(new Condition("elytra equipped", sensors -> world.elytraEquipped()), 100),
                new SetValue<>(ROCKET, ctx -> world.findSlot(Rockets::isPlain)),
                new SelectSlot(Rockets::isPlain, true, world, outputs), new TakeoffElytra(ROCKET, world, outputs),
                new Condition("gliding", sensors -> sensors.gliding()))))).keyedBy(ctx -> world.feet().toString()),
            new ResetBudget("takeoff"));
    }
}
