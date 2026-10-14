package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.mc.MineGeometry;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import com.hunterbuddy.behavior.nodes.ChooseGalleryStep;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.PlanStaircase;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.TryMiningChoice;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public final class MineLayerTree {
    private MineLayerTree() {}

    public static Node build(MineSiteContext mine) {
        return new Sequence("staircase-layer-galleries",
            new PlanStaircase(mine),
            new Deferred("reach-recorded-staircase-bottom", () -> GroundTravel.withoutBreaking(
                BlockPos.fromLong(mine.facts.stairs.getLast()), mine.baritone, mine.outputs)),
            new RepeatUntil("dig-and-traverse-staircase", () -> mine.steps.isEmpty() || mine.finished(), 384, stairStep(mine)),
            MineSiteTree.measure(mine),
            new RepeatUntil("mine-connected-layer-galleries", mine::finished, 1800,
                new Sequence("choose-clear-and-enter-gallery", new ChooseGalleryStep(mine),
                    LogisticsOperations.unless("gallery work finished", mine::finished,
                        new TryMiningChoice(mine, () -> mine.nextFeet, corridor(mine, false)))).resumable())).resumable();
    }

    private static Node stairStep(MineSiteContext mine) {
        return new Sequence("next-stair-tread",
            new InventoryMilestone("choose-stair-tread", ctx -> {
                mine.nextFeet = mine.steps.getFirst();
                mine.stand = BlockPos.fromLong(mine.facts.stairs.getLast());
                if (!MineGeometry.floor(mine.world, mine.nextFeet.down())) throw new IllegalStateException("stair floor changed");
                mine.protectSupport(mine.nextFeet.down());
                mine.save(ctx, "mine-site protect stair floor=" + mine.nextFeet.down().toShortString());
            }), corridor(mine, true)).resumable();
    }

    private static Node corridor(MineSiteContext mine, boolean staircase) {
        List<Node> nodes = new ArrayList<>();
        if (staircase) nodes.add(clear(mine, 2));
        nodes.add(clear(mine, 1));
        nodes.add(LogisticsOperations.unless("mining quota reached", mine::finished, clear(mine, 0)));
        Node traverse = new Sequence("enter-confirmed-carved-cell",
            new Deferred("native-travel-to-carved-tread", () -> GroundTravel.build(mine.nextFeet, 0, mine.baritone, mine.outputs)),
            new InventoryMilestone("record-traversed-mine-step", ctx -> {
                if (!MineGeometry.standing(mine.world, mine.nextFeet)) throw new IllegalStateException("carved step lost its support");
                if (staircase) {
                    mine.facts.stairs.add(mine.nextFeet.asLong());
                    mine.steps.removeFirst();
                } else mine.facts.gallery.add(mine.nextFeet.asLong());
                mine.protectSupport(mine.nextFeet.down());
                mine.save(ctx, "mine-site traversed=" + mine.nextFeet.toShortString());
                ctx.progress("carved mine step reached");
            })).resumable();
        nodes.add(LogisticsOperations.unless("quota reached before entering next cell", mine::finished, traverse));
        return new Sequence(staircase ? "three-high-stair-clearance" : "two-high-gallery-clearance",
            nodes.toArray(Node[]::new)).resumable();
    }

    private static Node clear(MineSiteContext mine, int height) {
        return new Deferred("clear-mine-corridor-height-" + height, () -> {
            BlockPos point = mine.nextFeet.up(height);
            return LogisticsOperations.unless("corridor cell already clear", () -> MineGeometry.clear(mine.world, point)
                && (mine.facts.pending == null || mine.facts.pending != point.asLong()),
                MineBlockTree.build(mine, point, mine.patch.contains(point) && mine.material(point), mine.stand));
        }).resumable();
    }
}
