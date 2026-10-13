package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.FarmerMissionContext;

public final class WaitForFarmerZone extends Node {
    private static final long PROOF_INTERVAL_MS = 60_000;
    private final FarmerMissionContext mission;
    private long provedAt;

    public WaitForFarmerZone(FarmerMissionContext mission) {
        super("attendre-à-la-base-la-zone-occupée");
        this.mission = mission;
    }

    @Override
    protected void onStart(TickContext ctx) {
        provedAt = ctx.nowMs() - PROOF_INTERVAL_MS;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        long remaining = mission.facts.waitUntil - System.currentTimeMillis();
        if (remaining > 0) {
            if (ctx.nowMs() - provedAt >= PROOF_INTERVAL_MS) {
                provedAt = ctx.nowMs();
                ctx.progress("attente planifiée à la base, zone occupée");
            }
            return new Running("zone occupée, attente à la base restante=" + remaining / 1000 + "s");
        }
        mission.facts.waitUntil = 0;
        mission.facts.waitAtBase = false;
        mission.facts.occupied.entrySet().removeIf(entry -> entry.getValue() <= System.currentTimeMillis());
        mission.save(ctx, "attente terminée, nouvelle sélection de site");
        return Success.INSTANCE;
    }
}
