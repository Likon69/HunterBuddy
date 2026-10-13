package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.CandidatePicker;
import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
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
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.nodes.BreakBlock;
import com.hunterbuddy.behavior.nodes.Budget;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.FireRocket;
import com.hunterbuddy.behavior.nodes.MarkFailed;
import com.hunterbuddy.behavior.nodes.PlaceBlock;
import com.hunterbuddy.behavior.nodes.ProgressGuard;
import com.hunterbuddy.behavior.nodes.Retry;
import com.hunterbuddy.behavior.nodes.SelectSlot;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.SetValue;
import com.hunterbuddy.behavior.nodes.SwimUp;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.WaitUntil;
import com.hunterbuddy.behavior.nodes.WaterJumpTap;
import com.hunterbuddy.behavior.nodes.WithLease;
import com.hunterbuddy.behavior.probes.BlockScan;
import com.hunterbuddy.behavior.probes.FindOpenSkyPoint;
import com.hunterbuddy.behavior.probes.FindTakeoffPoints;
import com.hunterbuddy.behavior.probes.ScanColumn;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.resource.SlotIntent;
import com.hunterbuddy.util.Rockets;
import java.util.Set;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public final class ExitWaterTree {
    private static final Key<BlockPos> HERE = new Key<>("exit-column", BlockPos.class, Scope.OWNER, false);
    private static final Key<BlockPos> ABOVE = new Key<>("exit-above", BlockPos.class, Scope.OWNER, false);
    private static final Key<BlockPos> BLOCK = new Key<>("exit-head-block", BlockPos.class, Scope.OWNER, false);
    private static final Key<BlockScan> HEAD = new Key<>("exit-head-scan", BlockScan.class, Scope.OWNER, false);
    private static final Key<BlockScan> SKY = new Key<>("exit-sky", BlockScan.class, Scope.OWNER, false);
    private static final Key<BlockScan> POINTS = new Key<>("exit-points", BlockScan.class, Scope.OWNER, false);
    private static final Key<TargetPoint> TARGET = new Key<>("exit-target", TargetPoint.class, Scope.OWNER, false);
    private static final Key<Integer> ROCKET = new Key<>("water-rocket-slot", Integer.class, Scope.OWNER, false);
    private ExitWaterTree() { }

    public static Node build(WorldReader world, BaritoneBridge bridge, Outputs outputs) {
        Node swim = new ProgressGuard(sensors -> sensors.y(), 0.5, 1_200, new Timeout(1_200,
            new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT, new SwimUp(HERE, world, outputs))))
            .keyedBy(ctx -> ctx.bb().get(ctx.owner(), HERE).orElseThrow().toString());
        Node sky = new Timeout(100, new FindOpenSkyPoint(HERE, SKY, world));
        Node water = new Budget("water-jump", 3, new Timeout(200, new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK, Resource.ROCKETS), LeasePolicy.WAIT,
            new WaterBoost(outputs, new Sequence("water-jump", new WaitUntil(new Condition("elytra equipped", sensors -> world.elytraEquipped()), 100),
                new SetValue<>(ROCKET, ctx -> world.findSlot(Rockets::isPlain)), new SelectSlot(Rockets::isPlain, true, world, outputs),
                new Timeout(40, new WaterJumpTap(-60, world, outputs)), new FireRocket(ROCKET, outputs)))))).keyedBy(ctx -> world.feet().toString());
        Node takeoff = new Retry(2, 0, TakeoffTree.build(world, outputs));
        Node find = new Timeout(2_400, new FindTakeoffPoints(HERE, POINTS, world));
        Node travel = GoToTree.build(TARGET, 1, world, bridge, outputs);
        Node ascend = new Budget("self-ascend", 1, new ProgressGuard(sensors -> sensors.y(), 1, 200,
            new Timeout(3_600, BreakColumnTree.build(ABOVE, 24, world, outputs)))).keyedBy(ctx -> ctx.bb().get(ctx.owner(), HERE).orElseThrow().toString());
        Node platform = new Budget("platform", 1, new Timeout(200, new WithLease(Set.of(Resource.LOOK, Resource.SLOT, Resource.INTERACT), LeasePolicy.WAIT,
            new Sequence("platform", new SetValue<>(BLOCK, ctx -> world.feet().down()),
                new PlaceBlock(BLOCK, stack -> stack.isOf(Items.OBSIDIAN), true, world, outputs))))).keyedBy(ctx -> world.feet().toString());
        Node headScan = new Timeout(100, new ScanColumn(ABOVE, 2, HEAD, world));
        Node mineHead = new Timeout(300, new WithLease(Set.of(Resource.MINING), LeasePolicy.WAIT, new BreakBlock(BLOCK, true, world, outputs)));
        return new TaskScope(new Timeout(9_600, new Escape(world, swim, sky, water, takeoff, find, travel, ascend, platform, headScan, mineHead)));
    }

    private static final class Escape extends Composite {
        private final WorldReader world;
        private final CandidatePicker<BlockPos> picker = new CandidatePicker<>();
        private Stage stage;
        private BlockPos spot;
        private int swimming;
        private int ascents;
        private boolean platformSpent;

        private Escape(WorldReader world, Node... branches) { super("exit-and-fly", branches); this.world = world; }

        @Override
        protected void onStart(TickContext ctx) { stage = Stage.PROBE; swimming = 0; ascents = 0; platformSpent = false; setHere(ctx); }

        private void setHere(TickContext ctx) {
            spot = world.feet(); ctx.bb().set(ctx.owner(), HERE, spot); ctx.bb().set(ctx.owner(), ABOVE, spot.up(2));
        }

        private void enter(TickContext ctx, Stage next) {
            for (Node node : children()) node.halt(ctx, ExitReason.PREEMPTED);
            stage = next;
        }

        @Override
        protected NodeResult onTick(TickContext ctx) {
            if (ctx.sensors().gliding() && stage != Stage.WATER && stage != Stage.TAKEOFF) return Success.INSTANCE;
            if (stage == Stage.PROBE) {
                setHere(ctx);
                if (ctx.sensors().submerged()) { enter(ctx, Stage.SWIM); return new Running("swim before takeoff"); }
                NodeResult probe = children().get(1).execute(ctx);
                if (!(probe instanceof Success)) return probe;
                boolean clear = !ctx.bb().get(ctx.owner(), SKY).orElseThrow().blocks().isEmpty();
                boolean failed = ctx.bb().get(ctx.owner(), MarkFailed.failedKey(spot.toString())).orElse(false);
                if (clear && !failed && (ctx.sensors().onGround() || ctx.sensors().inWater())) enter(ctx, ctx.sensors().inWater() ? Stage.WATER : Stage.TAKEOFF);
                else if (ctx.sensors().onGround() && !clear && ascents < 16) { ascents++; enter(ctx, Stage.ASCEND); }
                else enter(ctx, Stage.FIND);
                return new Running("choose exit branch=" + stage);
            }
            if (stage == Stage.SWIM && ++swimming >= 10 && !ctx.series().yBest(10)) {
                ctx.bb().set(ctx.owner(), ABOVE, world.feet().up());
                enter(ctx, Stage.HEAD_SCAN); return new Running("probe ceiling before mining");
            }
            if (stage == Stage.HEAD_SCAN) {
                NodeResult scanned = children().get(8).execute(ctx);
                if (!(scanned instanceof Success)) return scanned;
                var blocks = ctx.bb().get(ctx.owner(), HEAD).orElseThrow().blocks();
                if (blocks.isEmpty()) { swimming = 0; enter(ctx, Stage.FIND); }
                else { ctx.bb().set(ctx.owner(), BLOCK, blocks.getFirst()); enter(ctx, Stage.HEAD_MINE); }
                return new Running("water ceiling checked");
            }
            if (stage == Stage.FIND) {
                NodeResult scanned = children().get(4).execute(ctx);
                if (!(scanned instanceof Success)) return scanned;
                BlockPos target = picker.pick(ctx, ctx.owner(), path() + "/takeoff-points", ctx.bb().get(ctx.owner(), POINTS).orElseThrow().blocks(),
                    pos -> -Math.sqrt(Math.pow(pos.getX() - ctx.sensors().x(), 2) + Math.pow(pos.getY() - ctx.sensors().y(), 2) + Math.pow(pos.getZ() - ctx.sensors().z(), 2)), BlockPos::toString).orElse(null);
                if (target == null) {
                    if (!platformSpent) { platformSpent = true; enter(ctx, Stage.PLATFORM); return new Running("try resistant platform"); }
                    return fail(FailureCode.NO_OPTION, "no loaded takeoff point or usable ascent");
                }
                spot = target;
                ctx.bb().set(ctx.owner(), TARGET, new TargetPoint(target.getX() + 0.5, target.getY(), target.getZ() + 0.5));
                enter(ctx, Stage.TRAVEL); return new Running("go to takeoff point=" + target);
            }
            int branch = switch (stage) { case SWIM -> 0; case WATER -> 2; case TAKEOFF -> 3; case TRAVEL -> 5; case ASCEND -> 6; case PLATFORM -> 7; case HEAD_MINE -> 9; default -> throw new IllegalStateException("unexpected exit stage"); };
            NodeResult result = children().get(branch).execute(ctx);
            if (result instanceof Running) return result;
            if (result instanceof Failure failure) {
                if (failure.code() == FailureCode.MISSING_ITEM) return result;
                ctx.bb().set(ctx.owner(), MarkFailed.failedKey(spot.toString()), true);
                enter(ctx, Stage.FIND);
                return new Running("exit branch failed; select another point");
            }
            if (stage == Stage.WATER || stage == Stage.TAKEOFF) return Success.INSTANCE;
            swimming = 0;
            enter(ctx, Stage.PROBE);
            return new Running("exit branch completed; resample world");
        }

        private enum Stage { PROBE, SWIM, WATER, TAKEOFF, FIND, TRAVEL, ASCEND, PLATFORM, HEAD_SCAN, HEAD_MINE }
    }

    private static final class WaterBoost extends Decorator {
        private final Outputs outputs;
        private WaterBoost(Outputs outputs, Node child) { super("water-boost", child); this.outputs = outputs; }
        @Override
        protected NodeResult onTick(TickContext ctx) {
            int slot = ctx.bb().get(ctx.owner(), ROCKET).orElse(-1);
            if (slot >= 0 && slot < 9) outputs.slot(ctx.owner(), new SlotIntent(slot, true));
            return child().execute(ctx);
        }
    }
}
