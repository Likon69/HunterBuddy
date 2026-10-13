package com.hunterbuddy.behavior.nodes;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import com.hunterbuddy.behavior.core.*;
import com.hunterbuddy.behavior.farmer.MissionJournal;
import com.hunterbuddy.behavior.mc.*;
import com.hunterbuddy.behavior.resource.*;
import java.util.Comparator;
import java.util.function.Predicate;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

public final class PickupItem extends Action {
    private final WorldReader world;
    private final Outputs outputs;
    private final Predicate<ItemStack> filter;
    private final BlockPos origin;
    private final java.util.function.IntSupplier baseline;
    private final MissionJournal journal;
    private final String label;
    private final Node travel;
    private final Key<Goal> goal;
    private BlockPos target;
    private int ticks;
    private boolean arrived;

    public PickupItem(BlockPos origin, Predicate<ItemStack> filter, java.util.function.IntSupplier baseline, String label, WorldReader world, BaritoneBridge baritone, Outputs outputs, MissionJournal journal) {
        super("pickup-expected-item");
        this.origin = origin;
        this.filter = filter;
        this.baseline = baseline;
        this.label = label;
        this.world = world;
        this.outputs = outputs;
        this.journal = journal;
        goal = new Key<>("pickup-goal-" + label, Goal.class, Scope.OWNER, false);
        travel = new Timeout(300, new WithLease(java.util.Set.of(Resource.BARITONE), LeasePolicy.WAIT,
            new BaritoneGoTo(goal, () -> false, false, baritone, outputs)));
        limit(400);
    }

    @Override
    public java.util.List<Node> children() { return java.util.List.of(travel); }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (outputs.inventory().confirmedCount(filter) > baseline.getAsInt() && world.countMatching(filter) > baseline.getAsInt()) {
            journal.milestone(ctx, label + " picked-up at=" + origin.toShortString());
            return Success.INSTANCE;
        }
        ItemEntity found = world.client().world.getEntitiesByClass(ItemEntity.class,
            new net.minecraft.util.math.Box(origin).expand(8), entity -> entity.isAlive() && filter.test(entity.getStack())).stream()
            .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(world.client().player))).orElse(null);
        if (found == null) return ++ticks >= 100 ? fail(FailureCode.MISSING_ITEM, "expected drop not found at " + origin.toShortString()) : new Running("waiting for expected drop or inventory receipt");
        BlockPos next = found.getBlockPos();
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
        if (result instanceof Success) { arrived = true; return new Running("waiting for confirmed pickup"); }
        return result;
    }
}
