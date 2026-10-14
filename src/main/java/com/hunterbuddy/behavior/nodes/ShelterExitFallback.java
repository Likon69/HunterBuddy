package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Composite;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.LogisticsContext;

public final class ShelterExitFallback extends Composite {
    private final LogisticsContext logistics;

    public ShelterExitFallback(LogisticsContext logistics, Node top, Node ground) {
        super("shelter-top-exit-with-ground-fallback", top, ground);
        this.logistics = logistics;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (logistics.facts.exitTop) {
            NodeResult result = children().getFirst().execute(ctx);
            if (!(result instanceof Failure failure) || failure.code() == FailureCode.MISSION_STOP) return result;
            logistics.facts.exitTop = false;
            logistics.facts.groundExitRequired = true;
            logistics.save(ctx, "top shelter exit failed; ground exit selected: " + failure.reason());
        }
        return children().get(1).execute(ctx);
    }
}
