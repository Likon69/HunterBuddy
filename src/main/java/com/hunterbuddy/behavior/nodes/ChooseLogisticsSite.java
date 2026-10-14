package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsSite;
import net.minecraft.util.math.BlockPos;

public final class ChooseLogisticsSite extends Node {
    private final LogisticsContext logistics;

    public ChooseLogisticsSite(LogisticsContext logistics) {
        super("choisir-emplacement");
        this.logistics = logistics;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        LogisticsFacts facts = logistics.facts;
        if (facts.site != null) return Success.INSTANCE;
        facts.zone = logistics.zones.get().corners().stream().map(LogisticsFacts::coordinates).toList();
        facts.siteAttempts = Math.clamp(facts.siteAttempts, 0, LogisticsSite.MAX_ATTEMPTS);
        if (facts.siteAttempts == LogisticsSite.MAX_ATTEMPTS) return fail(FailureCode.NO_OPTION, "logistics site attempts exhausted");
        BlockPos pos = LogisticsSite.choose(logistics.world, facts.activeZone(), facts.siteAttempts);
        if (pos == null) return fail(FailureCode.NO_OPTION, "no remaining shelter site or platform inside the zone");
        facts.site = LogisticsFacts.coordinates(pos);
        logistics.mission.equipment(ctx, "logistics-chest", "");
        logistics.restore(ctx);
        logistics.save(ctx, "emplacement choisi at=" + pos.toShortString());
        return Success.INSTANCE;
    }
}
