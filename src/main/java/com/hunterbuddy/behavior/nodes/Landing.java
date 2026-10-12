package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;

public final class Landing extends Decorator {
    public Landing(Node child) {
        super("landing", child);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return child().execute(ctx);
    }
}
