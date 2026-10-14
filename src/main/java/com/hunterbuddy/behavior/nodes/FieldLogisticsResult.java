package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.LogisticsFacts;
import com.hunterbuddy.behavior.mc.FarmerMissionContext;
import com.hunterbuddy.behavior.mc.LogisticsContext;

public final class FieldLogisticsResult extends Decorator {
    private final FarmerMissionContext mission;
    private final LogisticsContext logistics;

    public FieldLogisticsResult(FarmerMissionContext mission, LogisticsContext logistics, Node child) {
        super("request-base-after-field-shelter-shortage", child);
        this.mission = mission;
        this.logistics = logistics;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        if (result instanceof Success && logistics.facts != null && logistics.facts.baseRequested) {
            mission.facts.baseRequested = true;
            mission.save(ctx, "base requested after confirmed field logistics cleanup");
        }
        if (!(result instanceof Failure failure) || logistics.facts == null
            || failure.code() != FailureCode.BUDGET && failure.code() != FailureCode.NO_OPTION) return result;
        if (logistics.facts.site != null && logistics.equipment("chest").placementRequested()) return result;
        logistics.facts.departure = LogisticsFacts.coordinates(logistics.world.feet());
        logistics.save(ctx, "field logistics deferred to base: " + failure.reason());
        mission.facts.baseRequested = true;
        mission.save(ctx, "base requested for loose inventory or shelter blocks: " + failure.reason());
        return Success.INSTANCE;
    }
}
