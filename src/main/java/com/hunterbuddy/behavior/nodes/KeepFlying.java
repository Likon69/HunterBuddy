package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.Conditions;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.probes.BlockScan;
import com.hunterbuddy.behavior.probes.FindTakeoffPoints;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.MovementIntent;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;
import net.minecraft.util.math.BlockPos;

public final class KeepFlying extends Composite {
    private static final Key<BlockPos> ORIGIN = new Key<>("landing-origin", BlockPos.class, Scope.OWNER, false);
    private static final Key<BlockScan> POINTS = new Key<>("landing-points", BlockScan.class, Scope.OWNER, false);
    private static final Key<BlockPos> TARGET = new Key<>("landing-target", BlockPos.class, Scope.OWNER, false);
    private final WorldReader world;
    private final BaritoneBridge bridge;
    private final Outputs outputs;
    private Stage stage;

    public KeepFlying(WorldReader world, BaritoneBridge bridge, Outputs outputs) {
        super("keep-flying",
            new Timeout(2_400, new Sequence("landing-probe", new SetValue<>(ORIGIN, ctx -> world.feet()),
                new FindTakeoffPoints(ORIGIN, POINTS, world))),
            new Landing(new WithLease(Set.of(Resource.BARITONE), LeasePolicy.WAIT, new Pilot(world, bridge, outputs, true))),
            new Landing(new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT, new Pilot(world, bridge, outputs, false))));
        this.world = world; this.bridge = bridge; this.outputs = outputs;
    }

    @Override
    protected void onStart(TickContext ctx) { stage = Stage.PROBE; }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (!ctx.sensors().gliding()) return Success.INSTANCE;
        if (stage == Stage.PROBE) {
            NodeResult result = children().getFirst().execute(ctx);
            if (result instanceof Running) {
                NodeResult flight = children().get(2).execute(ctx);
                return flight instanceof Failure ? flight : result;
            }
            children().get(2).halt(ctx, com.hunterbuddy.behavior.core.ExitReason.PREEMPTED);
            BlockPos target = outputs.lastGoal();
            if (target == null || !world.loaded(target) || !world.inBounds(target) || world.cleared(target.down())) {
                target = result instanceof Failure ? null : ctx.bb().get(ctx.owner(), POINTS).orElseThrow().blocks().stream()
                    .min(java.util.Comparator.comparingDouble(pos -> Math.hypot(pos.getX() - ctx.sensors().x(), pos.getZ() - ctx.sensors().z()))).orElse(null);
            }
            if (target != null) ctx.bb().set(ctx.owner(), TARGET, target);
            else ctx.bb().remove(ctx.owner(), TARGET);
            stage = target != null && bridge.elytraAvailable() ? Stage.NATIVE : Stage.FLAT;
        }
        return children().get(stage == Stage.NATIVE ? 1 : 2).execute(ctx);
    }

    private enum Stage { PROBE, NATIVE, FLAT }

    private static final class Pilot extends Action {
        private final WorldReader world;
        private final BaritoneBridge bridge;
        private final Outputs outputs;
        private final boolean nativeFlight;
        private int ticks;

        private Pilot(WorldReader world, BaritoneBridge bridge, Outputs outputs, boolean nativeFlight) {
            super(nativeFlight ? "baritone-landing" : "flat-landing");
            this.world = world; this.bridge = bridge; this.outputs = outputs; this.nativeFlight = nativeFlight; limit(12_000);
        }

        @Override
        protected void onActionStart(TickContext ctx) { ticks = 0; }

        @Override
        protected NodeResult onActionTick(TickContext ctx) {
            if (!ctx.sensors().gliding()) return Success.INSTANCE;
            BlockPos target = ctx.bb().get(ctx.owner(), TARGET).orElse(null);
            if (nativeFlight) {
                if (target == null) return fail(FailureCode.NO_OPTION, "landing destination missing");
                outputs.elytra(ctx.owner(), target, false);
                if (++ticks > 20 && !bridge.ownsFlight(target)) return fail(FailureCode.UNREACHABLE, "Baritone landing process ended");
            } else {
                float yaw = ctx.sensors().yaw();
                if (target != null) {
                    LookIntent desired = Conditions.towards(world.eyes(), new com.hunterbuddy.behavior.core.TargetPoint(target.getX() + 0.5, ctx.sensors().y(), target.getZ() + 0.5));
                    yaw += Math.clamp(Conditions.wrap(desired.yaw() - yaw), -30, 30);
                }
                outputs.look(ctx.owner(), new LookIntent(yaw, 5));
                outputs.movement(ctx.owner(), new MovementIntent(true, false, false, false, false, false, false));
            }
            return new Running("Landing: no rocket emission");
        }
    }
}
