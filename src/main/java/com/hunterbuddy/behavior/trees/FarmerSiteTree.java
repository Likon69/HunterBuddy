package com.hunterbuddy.behavior.trees;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.farmer.FarmerSettings;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import com.hunterbuddy.behavior.mc.FarmerFlightBudget;
import com.hunterbuddy.behavior.mc.FarmerMissionContext;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import com.hunterbuddy.behavior.mc.SiteArrival;
import com.hunterbuddy.behavior.nodes.Deferred;
import com.hunterbuddy.behavior.nodes.FarmerSiteResult;
import com.hunterbuddy.behavior.nodes.InventoryMilestone;
import com.hunterbuddy.behavior.nodes.Sequence;
import com.hunterbuddy.behavior.nodes.Succeed;
import com.hunterbuddy.behavior.nodes.Timeout;
import com.hunterbuddy.behavior.nodes.VerifySite;
import java.util.function.LongConsumer;
import net.minecraft.util.math.BlockPos;

public final class FarmerSiteTree {
    private FarmerSiteTree() {}

    public static Node mine(FarmerMissionContext mission, LongConsumer counted) {
        if (!mission.unfinishedMine() && !FarmerFlightBudget.canReach(mission.tick, mission.selected.seed(),
            mission.step.deliveryChest(), mission.world, mission.outputs)) {
            return new InventoryMilestone("insufficient-site-change-budget-return-to-base", ctx -> {
                FarmerFlightBudget.requestBase(ctx, mission, "restocking before reaching the next site, return reserve preserved");
            });
        }
        mission.facts.siteActive = true;
        mission.save(mission.tick, "site operation started with its recorded zone");
        MineSiteContext mine = new MineSiteContext(mission.selected, mission.profile, () -> mission.zone,
            mission.world, mission.baritone, mission.outputs, mission.journal);
        mine.retreatRequested = mission::retreatDue;
        return new FarmerSiteResult(mission, new Sequence("reach-verify-mine-and-return-to-surface",
            mission.unfinishedMine() ? new Succeed() : arrival(mission),
            new Timeout(FarmerSettings.ACTIVITY_TIMEOUT_TICKS, MineSiteTree.build(mine)),
            new InventoryMilestone("mining-finished-at-entrance", ctx -> {
                if (!mine.facts.exited) throw new IllegalStateException("mining has not returned to the surface, resume required");
                counted.accept(mine.facts.mined);
                boolean changing = mission.facts.changeRequested || !mission.eligible(mine.site);
                String changeReason = mission.facts.changeReason;
                mission.select(ctx, mine.site);
                if (changing || !mine.site.usable()) {
                    mission.facts.changeRequested = true;
                    mission.facts.changeReason = changeReason;
                    mission.save(ctx, "site exhausted or inaccessible: " + mine.site.reason);
                }
            })));
    }

    public static Node change(FarmerMissionContext mission) {
        return new Sequence("close-recover-and-select-another-site",
            ContainerTree.close(mission.outputs),
            new InventoryMilestone("choose-site-from-facts", ctx -> {
                if (mission.fieldActive() || mission.baseActive() || mission.unfinishedMine()) {
                    throw new IllegalStateException("tracked equipment or mining must be resumed before changing sites");
                }
                mission.avoidVisiblePlayers(ctx);
                if (!mission.facts.changeReason.isEmpty() && mission.selected != null) {
                    mission.selected.unavailable("inaccessible", mission.facts.changeReason);
                    mission.knowledge.save(ctx);
                }
                MaterialSite next = mission.best();
                boolean occupied = mission.occupiedOnly();
                mission.select(ctx, next);
                if (next == null && (occupied || mission.players())) {
                    mission.facts.baseRequested = true;
                    mission.facts.waitAtBase = true;
                    mission.save(ctx, "sites occupied: return and wait at base");
                }
            }));
    }

    private static Node arrival(FarmerMissionContext mission) {
        Key<BlockPos> verified = new Key<>("farmer-site-verified", BlockPos.class, Scope.OWNER, false);
        return new Sequence("fly-or-walk-then-validate-on-site",
            new Deferred("reach-site-zone", () -> {
                double distance = Math.hypot(mission.world.feet().getX() - mission.selected.x,
                    mission.world.feet().getZ() - mission.selected.z);
                if (mission.world.client().player.isOnGround() && distance <= 64) return new Succeed();
                return FlyToTree.build(() -> mission.selected.seed(), () -> mission.step.deliveryChest(), false,
                    mission.world, mission.baritone, mission.outputs);
            }),
            new Deferred("reach-chosen-site-support", () -> {
                mission.arrivalStand = SiteArrival.stand(mission.selected, mission.profile, mission.zone, mission.world,
                    mission.mineFacts());
                return GroundTravel.build(mission.arrivalStand, 0, mission.baritone, mission.outputs);
            }),
            new Timeout(2400, new VerifySite(mission.world, mission.zone, mission.profile, mission.knowledge,
                mission.sites, () -> mission.selected, () -> mission.arrivalStand, verified)));
    }
}
