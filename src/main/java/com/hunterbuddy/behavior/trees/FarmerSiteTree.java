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
            return new InventoryMilestone("budget-de-changement-insuffisant-retour-base", ctx -> {
                mission.facts.siteActive = false;
                mission.facts.baseRequested = true;
                mission.save(ctx, "ravitaillement avant d'atteindre le prochain site, réserve de retour préservée");
            });
        }
        mission.facts.siteActive = true;
        mission.save(mission.tick, "site operation started with its recorded zone");
        MineSiteContext mine = new MineSiteContext(mission.selected, mission.profile, () -> mission.zone,
            mission.world, mission.baritone, mission.outputs, mission.journal);
        mine.retreatRequested = mission::retreatDue;
        return new FarmerSiteResult(mission, new Sequence("rejoindre-vérifier-miner-et-remonter",
            mission.unfinishedMine() ? new Succeed() : arrival(mission),
            new Timeout(FarmerSettings.ACTIVITY_TIMEOUT_TICKS, MineSiteTree.build(mine)),
            new InventoryMilestone("minage-terminé-à-l'entrée", ctx -> {
                if (!mine.facts.exited) throw new IllegalStateException("minage non remonté, reprise requise");
                counted.accept(mine.facts.mined);
                boolean changing = mission.facts.changeRequested || !mission.eligible(mine.site);
                String changeReason = mission.facts.changeReason;
                mission.select(ctx, mine.site);
                if (changing || !mine.site.usable()) {
                    mission.facts.changeRequested = true;
                    mission.facts.changeReason = changeReason;
                    mission.save(ctx, "site épuisé ou inaccessible : " + mine.site.reason);
                }
            })));
    }

    public static Node change(FarmerMissionContext mission) {
        return new Sequence("fermer-reprendre-et-retenir-un-autre-site",
            ContainerTree.close(mission.outputs),
            new InventoryMilestone("choisir-un-site-selon-les-faits", ctx -> {
                if (mission.fieldActive() || mission.baseActive() || mission.unfinishedMine()) {
                    throw new IllegalStateException("matériel ou minage suivi à reprendre avant de changer de site");
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
                    mission.save(ctx, "sites occupés : rentrer et attendre à la base");
                }
            }));
    }

    private static Node arrival(FarmerMissionContext mission) {
        Key<BlockPos> verified = new Key<>("farmer-site-verified", BlockPos.class, Scope.OWNER, false);
        return new Sequence("vol-ou-marche-puis-validation-sur-place",
            new Deferred("rejoindre-la-zone-du-site", () -> {
                double distance = Math.hypot(mission.world.feet().getX() - mission.selected.x,
                    mission.world.feet().getZ() - mission.selected.z);
                if (mission.world.client().player.isOnGround() && distance <= 64) return new Succeed();
                return FlyToTree.build(() -> mission.selected.seed(), () -> mission.step.deliveryChest(), false,
                    mission.world, mission.baritone, mission.outputs);
            }),
            new Deferred("atteindre-l'accès-au-site", () -> GroundTravel.build(
                SiteArrival.stand(mission.selected, mission.profile, mission.zone, mission.world), 0, mission.baritone, mission.outputs)),
            new Timeout(2400, new VerifySite(mission.world, mission.zone, mission.profile, mission.knowledge,
                mission.sites, () -> mission.selected, verified)));
    }
}
