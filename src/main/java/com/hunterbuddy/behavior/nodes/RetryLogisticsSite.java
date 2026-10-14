package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.LogisticsRoles;
import com.hunterbuddy.behavior.mc.LogisticsContext;
import com.hunterbuddy.behavior.mc.LogisticsSite;

public final class RetryLogisticsSite extends Decorator {
    private final LogisticsContext logistics;

    public RetryLogisticsSite(LogisticsContext logistics, Node child) {
        super("location-fallback", child);
        this.logistics = logistics;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        if (!(result instanceof Failure failure)) return result;
        if (failure.code() == FailureCode.REFUSED || failure.code() == FailureCode.BUDGET
            || failure.code() == FailureCode.MISSING_ITEM || failure.code() == FailureCode.NO_OPTION || logistics.facts.building != null
            || logistics.facts.site != null && logistics.equipment(LogisticsRoles.CHEST).placementRequested()) return failure;
        logistics.facts.site = null;
        logistics.facts.reusedShelter = false;
        logistics.facts.walls.clear();
        logistics.facts.wallItems.clear();
        logistics.facts.siteAttempts = Math.min(LogisticsSite.MAX_ATTEMPTS, logistics.facts.siteAttempts + 1);
        if (logistics.facts.siteAttempts == LogisticsSite.MAX_ATTEMPTS) {
            return fail(FailureCode.NO_OPTION, "all bounded shelter site attempts failed");
        }
        logistics.save(ctx, "another site or raised platform: " + failure.reason());
        child().resetMemory();
        return new Running("choosing another site inside the zone");
    }
}
