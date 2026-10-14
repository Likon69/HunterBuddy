package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import java.util.function.Supplier;

public final class SiteAccess extends Decorator {
    private final Supplier<MaterialSite> selected;
    private final WorldKnowledge knowledge;
    private final boolean flight;

    public SiteAccess(Supplier<MaterialSite> selected, WorldKnowledge knowledge, boolean flight, Node child) {
        super("record-site-access-failure", child);
        this.selected = selected;
        this.knowledge = knowledge;
        this.flight = flight;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        MaterialSite site = selected.get();
        if (result instanceof Failure failure && site != null && site.usable()
            && (failure.code() == FailureCode.UNREACHABLE || failure.code() == FailureCode.NO_PROGRESS
                || failure.code() == FailureCode.NO_OPTION || failure.code() == FailureCode.TIMEOUT
                || flight && failure.code() == FailureCode.NOT_CONFIRMED)) {
            if (flight) site.avoidFlight(failure.reason());
            else site.unavailable("inaccessible", failure.reason());
            knowledge.saveNow(ctx);
        }
        return result;
    }
}
