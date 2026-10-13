package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.MineSiteContext;

public final class MineSiteResult extends Decorator {
    private final MineSiteContext mine;

    public MineSiteResult(MineSiteContext mine, Node child) {
        super("mining-result-with-exact-cause", child);
        this.mine = mine;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = child().execute(ctx);
        if (result instanceof Failure && !mine.failure.isEmpty()) return fail(FailureCode.UNREACHABLE, mine.failure);
        return result;
    }
}
