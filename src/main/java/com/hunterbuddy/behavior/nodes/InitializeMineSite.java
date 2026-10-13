package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.farmer.MineSiteFacts;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import com.hunterbuddy.behavior.mc.MineGeometry;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import net.minecraft.util.math.BlockPos;

public final class InitializeMineSite extends Node {
    private final MineSiteContext mine;

    public InitializeMineSite(MineSiteContext mine) {
        super("initialize-verified-site-mining-facts");
        this.mine = mine;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        mine.zone = mine.zoneSource.get();
        mine.rejected.clear();
        mine.failure = "";
        if (!mine.profile.strategy().equals("surface") && !mine.profile.strategy().equals("layer")) {
            return fail(FailureCode.UNSUPPORTED, "unknown mining strategy: " + mine.profile.strategy());
        }
        MineSiteFacts saved = mine.memory.load(ctx);
        boolean resume = saved != null && !saved.exited;
        if (resume) {
            if (saved.zone != null && !saved.zone.isEmpty()) {
                mine.zone = FarmerZone.restore(saved.zone.stream().map(LogisticsFacts::position).toList());
            }
            if (!saved.world.equals(mine.world.storageKey()) || !saved.profile.equals(mine.profile.key())
                || saved.site != mine.site.seed().asLong()) return fail(FailureCode.REFUSED, "unfinished mining belongs to another site");
            mine.facts = saved;
        } else {
            if (!mine.site.status.equals("verified") || mine.site.verifiedAt <= 0 || !mine.inside(mine.site.seed())) {
                return fail(FailureCode.REFUSED, "MineSiteTree requires a site verified inside the current zone");
            }
            if (!MineGeometry.standing(mine.world, mine.world.feet()) || !mine.inside(mine.world.feet())
                || !MineGeometry.clear(mine.world, mine.world.feet().up(2))) {
                return fail(FailureCode.UNREACHABLE, "mining entrance needs a supported position with headroom inside the zone");
            }
            mine.facts = new MineSiteFacts();
            mine.facts.world = mine.world.storageKey();
            mine.facts.profile = mine.profile.key();
            mine.facts.site = mine.site.seed().asLong();
            mine.facts.zone = mine.zone.corners().stream().map(LogisticsFacts::coordinates).toList();
            mine.facts.entrance = mine.world.feet().asLong();
            mine.protectSupport(mine.world.feet().down());
            if (saved != null && saved.exited && saved.world.equals(mine.facts.world) && saved.profile.equals(mine.facts.profile)
                && saved.entrance == mine.facts.entrance && saved.stairs.stream()
                    .map(BlockPos::fromLong).allMatch(point -> MineGeometry.standing(mine.world, point))) {
                mine.facts.stairs.addAll(saved.stairs);
                mine.facts.supports.addAll(saved.supports);
            }
            mine.save(ctx, "mine-site entrance=" + mine.world.feet().toShortString());
        }
        mine.knowledge = WorldKnowledge.load(ctx);
        mine.recordedZone = mine.zone;
        mine.knowledge.zone(mine.zone, mine.profile).sites.put(Long.toString(mine.facts.site), mine.site);
        mine.outputs.inventory().observePlayer();
        if (resume && mine.facts.pending != null) mine.facts.receiptVersion = mine.outputs.inventory().playerVersion() - 1;
        mine.patch.clear();
        if (resume && saved.blocks != null && !saved.blocks.isEmpty()) {
            for (Long position : saved.blocks) mine.patch.add(BlockPos.fromLong(position));
        } else if (mine.site.blocks != null) for (Long position : mine.site.blocks) mine.patch.add(BlockPos.fromLong(position));
        if (mine.facts.stairs.isEmpty()) mine.facts.stairs.add(mine.facts.entrance);
        if (mine.facts.pending != null) {
            BlockPos pending = BlockPos.fromLong(mine.facts.pending);
            if (!mine.facts.broken && mine.world.loaded(pending) && mine.world.cleared(pending)) {
                return fail(FailureCode.NOT_CONFIRMED, "interrupted break is air without a durable server confirmation at " + pending);
            }
        }
        return Success.INSTANCE;
    }
}
