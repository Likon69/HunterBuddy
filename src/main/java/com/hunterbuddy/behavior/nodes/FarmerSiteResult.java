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
import com.hunterbuddy.behavior.mc.FarmerFlightBudget;
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
        if (result instanceof Success && mission.selected != null) mission.facts.siteFailures.remove(mission.selected.shelterId);
        if (!(result instanceof Running) && !mission.unfinishedMine()) {
            mission.facts.siteActive = false;
            mission.save(ctx, "site operation finished");
        }
        if (!(result instanceof Failure failure)) return result;
        if (failure.code() == FailureCode.BUDGET || failure.code() == FailureCode.MISSING_ITEM) {
            FarmerFlightBudget.requestBase(ctx, mission, "base requested: " + failure.reason());
            return mission.unfinishedMine() ? result : Success.INSTANCE;
        }
        if (failure.code() != FailureCode.UNREACHABLE && failure.code() != FailureCode.NO_PROGRESS
            && failure.code() != FailureCode.NO_OPTION && failure.code() != FailureCode.TIMEOUT
            && failure.code() != FailureCode.NOT_CONFIRMED) {
            if (!mission.unfinishedMine()) ctx.stopBot(ExitReason.FAILED, "[" + failure.code() + "] " + failure.reason());
            return result;
        }
        int failures = mission.selected == null ? 0 : mission.facts.siteFailures.merge(mission.selected.shelterId, 1, Integer::sum);
        if (mission.selected != null && failures >= 3) {
            mission.selected.avoidFlight(failure.reason());
            mission.facts.changeRequested = true;
            mission.facts.changeReason = "";
            mission.knowledge.saveNow(ctx);
        }
        mission.save(ctx, "site attempt failed [" + failure.code() + "] count=" + failures + ": " + failure.reason());
        return mission.unfinishedMine() ? result : Success.INSTANCE;
    }
}
