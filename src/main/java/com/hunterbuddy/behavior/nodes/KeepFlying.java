package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.Conditions;
import com.hunterbuddy.behavior.mc.FlightTerrain;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.probes.BlockScan;
import com.hunterbuddy.behavior.probes.FindTakeoffPoints;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.MovementIntent;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.util.math.BlockPos;

public final class KeepFlying extends Composite {
    private static final Key<BlockPos> ORIGIN = new Key<>("landing-origin", BlockPos.class, Scope.OWNER, false);
    private static final Key<BlockScan> POINTS = new Key<>("landing-points", BlockScan.class, Scope.OWNER, false);
    private static final Key<BlockPos> TARGET = new Key<>("landing-target", BlockPos.class, Scope.OWNER, false);
    private final WorldReader world;
    private final BaritoneBridge bridge;
    private final Outputs outputs;
    private Stage stage;
    private final Supplier<BlockPos> preferred;
    private final Consumer<BlockPos> chosen;

    public KeepFlying(WorldReader world, BaritoneBridge bridge, Outputs outputs) {
        this(world, bridge, outputs, null);
    }

    public KeepFlying(WorldReader world, BaritoneBridge bridge, Outputs outputs, Supplier<BlockPos> preferred) {
        this(world, bridge, outputs, preferred, point -> {});
    }

    public KeepFlying(WorldReader world, BaritoneBridge bridge, Outputs outputs, Supplier<BlockPos> preferred,
                      Consumer<BlockPos> chosen) {
        super("keep-flying",
            new Timeout(2_400, new Sequence("landing-probe", new SetValue<>(ORIGIN, ctx -> world.feet()),
                new FindTakeoffPoints(ORIGIN, POINTS, world, pos -> FlightTerrain.landing(pos, world)))),
            new Landing(new WithLease(Set.of(Resource.BARITONE), LeasePolicy.WAIT, new Pilot(world, bridge, outputs, true))),
            new Landing(new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT, new Pilot(world, bridge, outputs, false))),
            new Timeout(200, new WithLease(Set.of(Resource.MOVEMENT, Resource.LOOK), LeasePolicy.WAIT,
                new RedeployGlide(world, outputs))));
        this.world = world; this.bridge = bridge; this.outputs = outputs;
        this.preferred = preferred;
        this.chosen = chosen;
    }

    @Override
    protected void onStart(TickContext ctx) { stage = Stage.PROBE; }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (!ctx.sensors().gliding()) {
            if (ctx.sensors().onGround() || ctx.sensors().inWater() || ctx.sensors().submerged()) return Success.INSTANCE;
            NodeResult result = children().get(3).execute(ctx);
            return result instanceof Success ? new Running("landing glide redeployed") : result;
        }
        if (stage == Stage.PROBE) {
            NodeResult result = children().getFirst().execute(ctx);
            if (result instanceof Running) {
                NodeResult flight = children().get(2).execute(ctx);
                return flight instanceof Failure ? flight : result;
            }
            children().get(2).halt(ctx, ExitReason.PREEMPTED);
            BlockPos target = preferred == null ? outputs.lastGoal() : preferred.get();
            if (!FlightTerrain.landing(target, world)) {
                target = result instanceof Failure ? null : ctx.bb().get(ctx.owner(), POINTS).orElseThrow().blocks().stream()
                    .filter(pos -> FlightTerrain.landing(pos, world))
                    .min(Comparator.comparingDouble(pos -> Math.hypot(pos.getX() - ctx.sensors().x(), pos.getZ() - ctx.sensors().z()))).orElse(null);
            }
            chosen.accept(target);
            if (target != null) ctx.bb().set(ctx.owner(), TARGET, target);
            else ctx.bb().remove(ctx.owner(), TARGET);
            stage = preferred == null && target != null && bridge.elytraAvailable() ? Stage.NATIVE : Stage.FLAT;
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
            if (!nativeFlight && (bridge.capture().inControl() || bridge.capture().pathing() || bridge.capture().calcInProgress())) {
                return new Running("waiting for native Baritone movement; no flight takeover");
            }
            if (!nativeFlight && !world.available()) return fail(FailureCode.UNREACHABLE, "Minecraft world is unavailable");
            BlockPos target = ctx.bb().get(ctx.owner(), TARGET).orElse(null);
            if (nativeFlight) {
                if (target == null) return fail(FailureCode.NO_OPTION, "landing destination missing");
                outputs.elytra(ctx.owner(), target, false);
                if (++ticks > 20 && !bridge.ownsFlight(target)) return fail(FailureCode.UNREACHABLE, "Baritone landing process ended");
            } else {
                float yaw = ctx.sensors().yaw();
                if (target != null) {
                    LookIntent desired = Conditions.towards(world.eyes(), new TargetPoint(target.getX() + 0.5, ctx.sensors().y(), target.getZ() + 0.5));
                    yaw = desired.yaw();
                }
                yaw = ctx.sensors().yaw() + Math.clamp(Conditions.wrap(
                    FlightTerrain.heading(world, ctx.sensors(), yaw) - ctx.sensors().yaw()), -30, 30);
                outputs.look(ctx.owner(), new LookIntent(yaw, 5));
                outputs.movement(ctx.owner(), new MovementIntent(true, false, false, false, false, false, false));
            }
            return new Running("Landing: no rocket emission");
        }
    }
}
