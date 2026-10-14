package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.TreeValidator;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.MineGeometry;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import com.hunterbuddy.behavior.trees.GroundTravel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class PrepareLogisticsSite extends Node {
    private final LogisticsFacts state;
    private final WorldReader world;
    private final Outputs outputs;
    private final List<BlockPos> positions = new ArrayList<>();
    private int index;
    private Node child;
    private final BaritoneBridge baritone;
    private boolean traveling;

    public PrepareLogisticsSite(LogisticsFacts state, WorldReader world, BaritoneBridge baritone, Outputs outputs) {
        super("excavate-logistics-place");
        this.state = state;
        this.world = world;
        this.outputs = outputs;
        this.baritone = baritone;
        for (int y = 2; y >= 0; y--) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) positions.add(state.origin().add(x, y, z));
        positions.add(state.origin().north().down());
        positions.add(state.door().down());
    }

    @Override
    public List<Node> children() {
        return child == null ? List.of() : List.of(child);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        while (index < positions.size()) {
            BlockPos pos = positions.get(index);
            if (!world.loaded(pos)) return new Running("loading excavation position");
            if (state.walls.stream().map(LogisticsFacts::position).anyMatch(pos::equals)) {
                index++;
                continue;
            }
            if (state.building != null && LogisticsFacts.position(state.building).equals(pos)
                && Registries.ITEM.getId(world.state(pos).getBlock().asItem()).toString()
                    .equals(state.buildingItem)) {
                index++;
                continue;
            }
            if (world.state(pos).isAir() || pos.getY() < state.origin().getY() && !world.state(pos).getFluidState().isEmpty()) {
                index++;
                continue;
            }
            if (!world.state(pos).getFluidState().isEmpty()) return fail(FailureCode.UNREACHABLE,
                "choose an elevated dry platform above the water");
            if (!state.activeZone().containsWithMargin(pos.getX() + 0.5, pos.getZ() + 0.5, 3) || world.state(pos).hasBlockEntity()
                || world.hardness(pos) < 0 || outputs.mining().protectedBlock(pos)) return fail(FailureCode.UNREACHABLE,
                    "choose another logistics place: protected terrain");
            if (child != null && !traveling && pos.equals(world.feet().down())) {
                child.halt(ctx, ExitReason.PREEMPTED);
                child = null;
            }
            if (child == null) {
                if (pos.equals(world.feet().down())
                    || world.client().player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > 4.5 * 4.5) {
                    BlockPos stand = safeStanding(pos);
                    if (stand == null) return fail(FailureCode.UNREACHABLE, "no safe supported position for shelter excavation");
                    child = GroundTravel.build(stand, 0, baritone, outputs);
                    traveling = true;
                    TreeValidator.validate(child);
                    TreeValidator.bind(child, path() + "/reach-excavation", ctx.owner(), hook());
                    return new Running("moving within reach of the excavation");
                }
                Key<BlockPos> key = new Key<>("logistics-excavation", BlockPos.class, Scope.OWNER, false);
                child = new Timeout(300, new WithLease(Set.of(Resource.MINING), LeasePolicy.WAIT,
                    new Sequence("clear-logistics-space", new SetValue<>(key, tick -> pos),
                        new Guard(new Condition("excavation does not remove player support", sensors -> !pos.equals(world.feet().down())),
                            new BreakBlock(key, false, world, outputs)))));
                TreeValidator.validate(child);
                TreeValidator.bind(child, path() + "/excavate", ctx.owner(), hook());
            }
            NodeResult result = child.execute(ctx);
            if (result instanceof Failure) return result;
            if (traveling && result instanceof Success) {
                child = null;
                traveling = false;
                return new Running("excavation reached");
            }
            if (result instanceof Success) {
                child = null;
                index++;
                return new Running("excavating flat dry logistics space");
            }
            return result;
        }
        return Success.INSTANCE;
    }

    private BlockPos safeStanding(BlockPos target) {
        BlockPos best = null;
        double score = Double.POSITIVE_INFINITY;
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = -1; y <= 1; y++) {
            BlockPos feet = world.feet().add(x, y, z);
            if (feet.down().equals(target) || feet.down().equals(state.origin().north().down())
                || feet.down().equals(state.door().down()) || !MineGeometry.standing(world, feet)
                || !world.state(feet).getFluidState().isEmpty()
                || !state.activeZone().containsWithMargin(feet.getX() + 0.5, feet.getZ() + 0.5, 3)) continue;
            Vec3d eyes = Vec3d.ofBottomCenter(feet).add(0, world.client().player.getStandingEyeHeight(), 0);
            if (eyes.squaredDistanceTo(Vec3d.ofCenter(target)) > 4.5 * 4.5) continue;
            double candidate = feet.getSquaredDistance(world.feet());
            if (candidate < score) { score = candidate; best = feet; }
        }
        return best;
    }
}
