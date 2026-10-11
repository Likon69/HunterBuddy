package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;

public final class Succeed extends Node {
    public Succeed() {
        super("succeed");
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return Success.INSTANCE;
    }
}
