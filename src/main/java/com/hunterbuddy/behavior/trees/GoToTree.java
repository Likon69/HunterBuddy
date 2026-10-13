package com.hunterbuddy.behavior.trees;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalNear;
import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.Conditions;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.BaritoneGoTo;
import com.hunterbuddy.behavior.nodes.BreakBlock;
import com.hunterbuddy.behavior.nodes.Budget;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Parallel;
import com.hunterbuddy.behavior.nodes.ParallelPolicy;
import com.hunterbuddy.behavior.nodes.PillarUp;
import com.hunterbuddy.behavior.nodes.ProgressGuard;
import com.hunterbuddy.behavior.nodes.RestartPolicy;
import com.hunterbuddy.behavior.nodes.Retry;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WaitUntil;
import com.hunterbuddy.behavior.nodes.WalkTowards;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.probes.BlockScan;
import com.hunterbuddy.behavior.probes.FindBlocks;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.List;
import java.util.Set;
import net.minecraft.block.FluidBlock;
import net.minecraft.util.math.BlockPos;

public final class GoToTree {
    private static final Key<Goal> GOAL = new Key<>("goto-goal", Goal.class, Scope.TASK, false);
    private static final Key<BlockPos> FRONT = new Key<>("goto-front", BlockPos.class, Scope.TASK, false);
    private static final Key<BlockScan> FRONT_SCAN = new Key<>("goto-front-scan", BlockScan.class, Scope.TASK, false);
    private static final Key<BlockPos> BLOCK = new Key<>("goto-block", BlockPos.class, Scope.TASK, false);
    private static final List<String> BUDGETS = List.of("baritone", "walk", "break-front", "pillar");

    private GoToTree() {
    }

    public static Node build(Key<TargetPoint> targetKey, TargetPoint target, double radius, WorldReader world,
                             BaritoneBridge baritone, Outputs outputs) {
        Node path = new Sequence("baritone-reach",
            new SetValue<>(GOAL, ctx -> new GoalNear(BlockPos.ofFloored(target.x(), target.y(), target.z()), Math.max(1, (int) Math.floor(radius)))),
            new Retry(3, 20, new Budget("baritone", 3,
                new ProgressGuard(sensors -> -Conditions.distance(sensors, target), 1, 240,
                    new Timeout(2_400, new WithLease(Set.of(Resource.BARITONE, Resource.MINING), LeasePolicy.WAIT,
                        new BaritoneGoTo(GOAL, true, false, baritone, outputs).limit(2_400))))
                    .keyedBy(ctx -> "baritone/" + target)).keyedBy(ctx -> target.toString())),
            near(target, radius));
        Node breakFront = new Sequence("break-front",
            new WaitUntil(new Condition("blocked ahead", sensors -> sensors.horizontalCollision()), 1_200),
            new SetValue<>(FRONT, ctx -> front(ctx, target)),
            new FindBlocks(FRONT, 0, 2, state -> !state.isAir() && !(state.getBlock() instanceof FluidBlock), FRONT_SCAN, world),
            new SetValue<>(BLOCK, ctx -> ctx.bb().get(ctx.owner(), FRONT_SCAN).orElseThrow().blocks().stream()
                .filter(pos -> world.loaded(pos) && !world.cleared(pos)).findFirst()
                .orElse(ctx.bb().get(ctx.owner(), FRONT).orElseThrow())),
            new Budget("break-front", 10, new Timeout(300, new BreakBlock(BLOCK, false, world, outputs)))
                .keyedBy(ctx -> target.toString()));
        Node walk = new Sequence("walk-reach",
            new Budget("walk", 2, new ProgressGuard(sensors -> -Conditions.distance(sensors, target), 1, 100,
                new Timeout(1_200, new WithLease(Set.of(Resource.MOVEMENT, Resource.MINING), LeasePolicy.WAIT,
                    new Parallel("walk", ParallelPolicy.MAIN, RestartPolicy.restart(20),
                        new WalkTowards(targetKey, radius, world, outputs), breakFront))))
                .keyedBy(ctx -> "walk/" + target)).keyedBy(ctx -> target.toString()), near(target, radius));
        Node pillar = new Sequence("pillar-reach",
            new Condition("target above by 3", sensors -> target.y() - sensors.y() >= 3),
            new Budget("pillar", 1, new ProgressGuard(sensors -> sensors.y(), 1, 200,
                new Timeout(1_800, new WithLease(Set.of(Resource.MOVEMENT, Resource.SLOT, Resource.LOOK, Resource.INTERACT),
                    LeasePolicy.WAIT, new PillarUp(target.y(), world, outputs))))
                .keyedBy(ctx -> "pillar/" + target)).keyedBy(ctx -> target.toString()), near(target, radius));
        return new TaskScope(new Timeout(12_000, new Reach(target, new Selector("reach", near(target, radius), path, walk, pillar))));
    }

    private static Node near(TargetPoint target, double radius) {
        return new Node("near target") {
            @Override
            protected NodeResult onTick(TickContext ctx) {
                if (!Conditions.near(ctx.sensors(), target, radius)) return fail(FailureCode.CONDITION_FALSE, "outside arrival radius");
                return ctx.monitor().samplesValid(ctx.tick()) ? Success.INSTANCE : new Running("arrival rejected=setback");
            }
        };
    }

    public static Node build(Key<TargetPoint> targetKey, double radius, WorldReader world, BaritoneBridge baritone, Outputs outputs) {
        return new Node("dynamic-goto") {
            private Node child;
            @Override
            public java.util.List<Node> children() { return child == null ? java.util.List.of() : java.util.List.of(child); }
            @Override
            protected void onStart(TickContext ctx) { child = null; }
            @Override
            protected NodeResult onTick(TickContext ctx) {
                if (child == null) {
                    TargetPoint target = ctx.bb().get(ctx.owner(), targetKey).orElse(null);
                    if (target == null) return fail(FailureCode.NO_OPTION, "goto target missing");
                    child = GoToTree.build(targetKey, target, radius, world, baritone, outputs);
                    com.hunterbuddy.behavior.core.TreeValidator.validate(child);
                    com.hunterbuddy.behavior.core.TreeValidator.bind(child, path() + "/instance", ctx.owner(), hook());
                }
                return child.execute(ctx);
            }
        };
    }

    private static BlockPos front(TickContext ctx, TargetPoint target) {
        double dx = target.x() - ctx.sensors().x();
        double dz = target.z() - ctx.sensors().z();
        double length = Math.hypot(dx, dz);
        return length == 0 ? BlockPos.ofFloored(ctx.sensors().x(), ctx.sensors().y() + 2, ctx.sensors().z())
            : BlockPos.ofFloored(ctx.sensors().x() + dx / length * 0.8, ctx.sensors().y(), ctx.sensors().z() + dz / length * 0.8);
    }

    private static final class Reach extends Decorator {
        private final TargetPoint target;

        private Reach(TargetPoint target, Node child) {
            super("goto", child);
            this.target = target;
        }

        @Override
        protected NodeResult onTick(TickContext ctx) {
            ctx.goalDistance(target.toString(), Conditions.distance(ctx.sensors(), target));
            NodeResult result = child().execute(ctx);
            if (result instanceof Success) for (String budget : BUDGETS) ctx.budgets().reset(ctx.owner(), budget);
            return result instanceof Failure ? fail(FailureCode.UNREACHABLE, "all options spent") : result;
        }
    }
}
