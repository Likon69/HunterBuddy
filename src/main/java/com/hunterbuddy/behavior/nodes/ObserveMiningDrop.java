package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.MineSiteContext;

public final class ObserveMiningDrop extends Action {
    private final MineSiteContext mine;

    public ObserveMiningDrop(MineSiteContext mine) {
        super("observe-real-server-mining-drops");
        this.mine = mine;
        limit(100);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        if (!mine.facts.broken) return fail(FailureCode.NOT_CONFIRMED, "drop observation requires a confirmed break");
        if (!mine.receipt.observe()) return new Running("waiting for dropped entity or inventory update");
        mine.save(ctx, "mine-site drops observed");
        return Success.INSTANCE;
    }
}
