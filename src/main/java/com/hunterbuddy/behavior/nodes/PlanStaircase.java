package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import com.hunterbuddy.behavior.mc.StaircasePlan;

public final class PlanStaircase extends Node {
    private final MineSiteContext mine;

    public PlanStaircase(MineSiteContext mine) {
        super("plan-supported-staircase-to-verified-layer");
        this.mine = mine;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        if (mine.facts.stairs.isEmpty()) mine.facts.stairs.add(mine.facts.entrance);
        try {
            mine.steps.clear();
            mine.steps.addAll(StaircasePlan.choose(mine));
            mine.save(ctx, "mine-site staircase planned");
            return Success.INSTANCE;
        } catch (IllegalStateException ex) {
            return fail(FailureCode.UNREACHABLE, ex.getMessage());
        }
    }
}
