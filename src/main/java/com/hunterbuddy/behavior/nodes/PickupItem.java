package com.hunterbuddy.behavior.nodes;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import com.hunterbuddy.behavior.core.Action;
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
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.LeasePolicy;
import com.hunterbuddy.behavior.resource.Outputs;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class PickupItem extends Action {
    private final WorldReader world;
    private final Outputs outputs;
    private final Predicate<ItemStack> filter;
    private final Predicate<BlockPos> allowed;
    private final Function<BlockPos, BlockPos> pickupPoint;
    private final BlockPos origin;
    private final IntSupplier baseline;
    private final MissionJournal journal;
    private final String label;
    private final Node travel;
    private final Key<Goal> goal;
    private BlockPos target;
    private int ticks;
    private boolean arrived;

    public PickupItem(BlockPos origin, Predicate<ItemStack> filter, IntSupplier baseline, String label,
                      WorldReader world, BaritoneBridge baritone, Outputs outputs, MissionJournal journal) {
        this(origin, filter, baseline, label, world, baritone, outputs, journal, position -> true);
    }

    public PickupItem(BlockPos origin, Predicate<ItemStack> filter, IntSupplier baseline, String label,
                      WorldReader world, BaritoneBridge baritone, Outputs outputs, MissionJournal journal, Predicate<BlockPos> allowed) {
        this(origin, filter, baseline, label, world, baritone, outputs, journal, allowed, position -> position);
    }

    public PickupItem(BlockPos origin, Predicate<ItemStack> filter, IntSupplier baseline, String label,
                      WorldReader world, BaritoneBridge baritone, Outputs outputs, MissionJournal journal, Predicate<BlockPos> allowed,
                      Function<BlockPos, BlockPos> pickupPoint) {
        super("pickup-expected-item");
        this.origin = origin;
        this.filter = filter;
        this.allowed = allowed;
        this.pickupPoint = pickupPoint;
        this.baseline = baseline;
        this.label = label;
        this.world = world;
        this.outputs = outputs;
        this.journal = journal;
        goal = new Key<>("pickup-goal-" + label, Goal.class, Scope.OWNER, false);
        travel = new TaskScope(new Timeout(300, new WithLease(Set.of(Resource.BARITONE), LeasePolicy.WAIT,
            new BaritoneGoTo(goal, () -> false, false, baritone, outputs))));
        limit(400);
    }

    @Override
    public List<Node> children() {
        return List.of(travel);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (outputs.inventory().confirmedCount(filter) > baseline.getAsInt() && world.countMatching(filter) > baseline.getAsInt()) {
            journal.milestone(ctx, label + " picked-up at=" + origin.toShortString());
            return Success.INSTANCE;
        }
        ItemEntity found = world.client().world.getEntitiesByClass(ItemEntity.class,
            new Box(origin).expand(8), entity -> entity.isAlive() && filter.test(entity.getStack())
                && allowed.test(entity.getBlockPos())).stream()
            .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(world.client().player))).orElse(null);
        if (found == null) {
            return ++ticks >= 100 ? fail(FailureCode.MISSING_ITEM, "expected drop not found at " + origin.toShortString())
                : new Running("waiting for expected drop or inventory receipt");
        }
        BlockPos next = pickupPoint.apply(found.getBlockPos());
        if (next == null) return fail(FailureCode.UNREACHABLE, "no safe position near expected drop");
        if (!next.equals(target)) {
            travel.halt(ctx, ExitReason.PREEMPTED);
            travel.resetMemory();
            arrived = false;
            target = next;
            ctx.bb().set(ctx.owner(), goal, new GoalBlock(target));
        }
        if (arrived) return new Running("waiting for inventory arrival");
        NodeResult result = travel.execute(ctx);
        if (result instanceof Failure) return result;
        if (result instanceof Success) {
            arrived = true;
            return new Running("waiting for confirmed pickup");
        }
        return result;
    }
}
