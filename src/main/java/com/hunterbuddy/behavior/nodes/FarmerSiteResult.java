package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.FarmerMissionContext;

public final class FarmerSiteResult extends Decorator {
    private final FarmerMissionContext mission;

    public FarmerSiteResult(FarmerMissionContext mission, Node child) {
        super("site-result-or-confirmed-change", child);
        this.mission = mission;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        if (!(result instanceof Running) && !mission.unfinishedMine()) {
            mission.facts.siteActive = false;
            mission.save(ctx, "site operation finished");
        }
        if (!(result instanceof Failure failure)) return result;
        if (mission.unfinishedMine()) return result;
        if (failure.code() == FailureCode.BUDGET || failure.code() == FailureCode.MISSING_ITEM) {
            mission.facts.baseRequested = true;
            mission.save(ctx, "base requested: " + failure.reason());
            return Success.INSTANCE;
        }
        if (failure.code() != FailureCode.UNREACHABLE && failure.code() != FailureCode.NO_PROGRESS
            && failure.code() != FailureCode.NO_OPTION && failure.code() != FailureCode.TIMEOUT
            && failure.code() != FailureCode.NOT_CONFIRMED) {
            ctx.stopBot(ExitReason.FAILED, "[" + failure.code() + "] " + failure.reason());
            return result;
        }
        if (mission.selected != null && !mission.selected.flightAvoided() && failure.code() != FailureCode.NOT_CONFIRMED) {
            mission.selected.unavailable("inaccessible", failure.reason());
            mission.knowledge.save(ctx);
        }
        mission.facts.changeRequested = true;
        mission.save(ctx, "site change: " + failure.reason());
        return Success.INSTANCE;
    }
}
