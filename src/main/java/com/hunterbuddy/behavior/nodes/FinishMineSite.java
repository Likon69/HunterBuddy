package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import com.hunterbuddy.behavior.mc.PatchMeasurement;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.util.math.BlockPos;

public final class FinishMineSite extends Node {
    private final MineSiteContext mine;

    public FinishMineSite(MineSiteContext mine) {
        super("persist-mining-result-after-return");
        this.mine = mine;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (mine.facts == null) return fail(FailureCode.REFUSED, mine.failure);
        if (mine.facts.pending != null) {
            BlockPos pending = BlockPos.fromLong(mine.facts.pending);
            boolean loaded = mine.world.loaded(pending);
            boolean present = loaded && !mine.world.cleared(pending);
            if (!present && mine.facts.broken) {
                mine.loseBlock(ctx, "uncollected mining drop closed on retreat at " + pending.toShortString());
            } else mine.closeBlock(ctx, !loaded ? "unconfirmed mining target rejected on retreat: chunk unavailable at " + pending.toShortString()
                : present ? "mining target rejected on retreat: block remains at " + pending.toShortString()
                : "unconfirmed empty mining cell skipped on retreat at " + pending.toShortString());
            if (present || !loaded) mine.rejected.add(pending);
        }
        mine.facts.exited = true;
        mine.patch.removeIf(point -> mine.world.loaded(point) && !mine.profile.blocks().contains(mine.world.state(point).getBlock()));
        mine.site.blocks = new ArrayList<>(mine.patch.stream().map(point -> point.asLong()).toList());
        mine.site.quantity = mine.site.blocks.size();
        mine.site.lowerBound = true;
        mine.site.verifiedAt = 0;
        mine.site.status = !mine.failure.isEmpty() && mine.facts.outcome.isEmpty() ? "inaccessible" : mine.facts.siteState;
        mine.site.reason = mine.failure.isEmpty() ? mine.facts.outcome : mine.failure;
        if (mine.facts.exited) {
            BlockPos next = mine.patch.stream().filter(point -> mine.world.loaded(point)
                && mine.recordedZone.containsWithMargin(point.getX() + 0.5, point.getZ() + 0.5, FarmerZoneLookup.TARGET_MARGIN))
                .filter(point -> PatchMeasurement.exposed(point, mine.world))
                .min(Comparator.comparingDouble(point -> point.getSquaredDistance(mine.world.feet()))).orElse(null);
            if (next != null) {
                var sites = mine.knowledge.zone(mine.recordedZone, mine.profile).sites;
                sites.remove(Long.toString(mine.facts.site));
                mine.site.x = next.getX();
                mine.site.y = next.getY();
                mine.site.z = next.getZ();
                sites.put(Long.toString(next.asLong()), mine.site);
            }
        }
        mine.knowledge.save(ctx);
        mine.save(ctx, "mine-site returned, mined=" + mine.facts.mined + ", excavated=" + mine.facts.excavated
            + ", lost=" + mine.facts.lostBlocks.size());
        if (!mine.failure.isEmpty() && mine.facts.outcome.isEmpty()) return fail(FailureCode.UNREACHABLE, mine.failure);
        return Success.INSTANCE;
    }
}
