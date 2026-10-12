package com.hunterbuddy.behavior.nodes;

import baritone.api.pathing.goals.Goal;
import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trace.Kind;
import java.util.Set;

public final class GateCombat extends Composite {
    private static final Key<Goal> GOAL = new Key<>("flee-goal", Goal.class, Scope.TASK, false);
    private Stage stage;

    public GateCombat(WorldReader world, BaritoneBridge bridge, Outputs outputs) {
        super("combat gate", new Timeout(1_200, new WaitUntil(new Condition("combat target gone", sensors -> !sensors.combatTarget()), 1_200)),
            new TaskScope(new Sequence("flee", new SetValue<>(GOAL, ctx -> world.awayFrom(ctx.sensors(), 48)),
                new Timeout(1_200, new WithLease(Set.of(Resource.BARITONE), LeasePolicy.WAIT,
                    new BaritoneGoTo(GOAL, false, false, bridge, outputs))))));
    }

    @Override
    protected void onStart(TickContext ctx) { stage = Stage.WAITING; }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (stage == Stage.WAITING) {
            NodeResult result = children().getFirst().execute(ctx);
            if (!(result instanceof Failure)) return result;
            ctx.yieldTask();
            ctx.event(this, Kind.GATE, null, "flee", "combat wait exceeded 60s; away=48");
            stage = Stage.FLEEING;
        }
        NodeResult result = children().get(1).execute(ctx);
        return result instanceof Running ? new Running("combat flee: waiting for available legacy resources") : result;
    }

    private enum Stage { WAITING, FLEEING }
}
