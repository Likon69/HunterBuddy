package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.LogisticsContext;

public final class RetryLogisticsSite extends Decorator {
    private final LogisticsContext logistics;

    public RetryLogisticsSite(LogisticsContext logistics, Node child) {
        super("repli-emplacement", child);
        this.logistics = logistics;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        if (!(result instanceof Failure failure)) return result;
        if (failure.code() == FailureCode.REFUSED || logistics.facts.building != null
            || logistics.facts.site != null && logistics.equipment("chest").placementRequested()) return failure;
        logistics.facts.site = null;
        logistics.facts.walls.clear();
        logistics.facts.wallItems.clear();
        logistics.facts.siteAttempts++;
        logistics.save(ctx, "another site or raised platform: " + failure.reason());
        child().resetMemory();
        return new Running("choosing another site inside the zone");
    }
}
