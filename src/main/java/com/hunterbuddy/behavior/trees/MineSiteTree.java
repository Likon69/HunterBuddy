package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import com.hunterbuddy.behavior.nodes.ChooseSurfaceBlock;
import com.hunterbuddy.behavior.nodes.Condition;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.FinishMineSite;
import com.hunterbuddy.behavior.nodes.Guard;
import com.hunterbuddy.behavior.nodes.InitializeMineSite;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.MineSiteResult;
import com.hunterbuddy.behavior.nodes.MineSiteScope;
import com.hunterbuddy.behavior.nodes.MiningBounds;
import com.hunterbuddy.behavior.nodes.ReactiveSelector;
import com.hunterbuddy.behavior.nodes.RepeatUntil;
import com.hunterbuddy.behavior.nodes.ResumeMineWork;
import com.hunterbuddy.behavior.nodes.Selector;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.TaskScope;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.TryMiningChoice;
import com.hunterbuddy.behavior.nodes.VerifySite;
import net.minecraft.util.math.BlockPos;

public final class MineSiteTree {
    private MineSiteTree() {}

    public static Node build(MineSiteContext mine) {
        Node work = new Sequence("mine-verified-site-by-profile",
            new InitializeMineSite(mine),
            new Deferred("reconcile-pending-mining-drop", () -> LogisticsOperations.unless("no unfinished block",
                () -> mine.facts.pending == null, new Deferred("resume-confirmed-mining-block", () ->
                    MineBlockTree.build(mine, BlockPos.fromLong(mine.facts.pending), mine.facts.material,
                        BlockPos.fromLong(mine.facts.stand))))),
            new Deferred("select-profile-mining-strategy", () -> LogisticsOperations.unless("mining goal already confirmed",
                mine::finished, mine.profile.strategy().equals("surface") ? surface(mine) : MineLayerTree.build(mine)))
                .resumable(), MineReturnTree.build(mine), new FinishMineSite(mine)).resumable();
        Node retreat = new Sequence("retreat-after-mining-failure", MineReturnTree.build(mine), new FinishMineSite(mine)).resumable();
        Node bounded = new Selector("mining-with-carved-access-retreat", new MineSiteScope(mine, work), retreat);
        return new TaskScope(new MineSiteResult(mine, new MiningBounds(mine.outputs.mining(), mine::inside,
            new ReactiveSelector("mining-with-water-recovery",
                new Guard(new Condition("oxygen recovery needed", mine::needsBreath),
                    new Sequence("recover-oxygen-then-resume", MineWaterTree.build(mine), new ResumeMineWork())),
                new Guard(new Condition("mining return still pending", sensors -> mine.facts == null || !mine.facts.exited), bounded)))));
    }

    public static Node measure(MineSiteContext mine) {
        Key<BlockPos> verified = new Key<>("mine-site-verified", BlockPos.class, Scope.OWNER, false);
        return new Sequence("reuse-or-refresh-connected-material-proof",
            LogisticsOperations.unless("connected patch already verified", () -> !mine.patch.isEmpty(),
                new Deferred("refresh-proof-from-carved-access", () -> new Sequence("reach-and-reverify-access",
                    GroundTravel.build(BlockPos.fromLong(mine.facts.stairs.getLast()), 0, mine.baritone, mine.outputs),
                    new Timeout(2400, new VerifySite(mine.world, mine.zone, mine.profile, mine.knowledge,
                        mine.knowledge.zone(mine.zone, mine.profile), () -> mine.site,
                        () -> BlockPos.fromLong(mine.facts.stairs.getLast()), verified)),
                    new InventoryMilestone("refresh-remaining-material-estimate", ctx -> {
                        mine.facts.estimatedQuantity = mine.site.quantity + mine.facts.mined;
                        mine.save(ctx, "remaining connected material estimate refreshed");
                    })))),
            new InventoryMilestone("load-verified-material-members", ctx -> {
                if (mine.patch.isEmpty()) for (Long position : mine.site.blocks) mine.patch.add(BlockPos.fromLong(position));
            }));
    }

    private static Node surface(MineSiteContext mine) {
        return new Sequence("surface-slices-from-the-top", measure(mine),
            new RepeatUntil("mine-highest-connected-slice", mine::finished, 1800,
                new Sequence("choose-reach-break-and-pick-up", new Timeout(2400, new ChooseSurfaceBlock(mine)),
                    LogisticsOperations.unless("surface work finished", mine::finished,
                        new TryMiningChoice(mine, () -> mine.target,
                            new Deferred("mine-selected-surface-block", () -> MineBlockTree.build(mine, mine.target, true, mine.stand))
                                .resumable()))).resumable())).resumable();
    }
}
