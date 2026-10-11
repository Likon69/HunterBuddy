package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Failure;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.TickContext;

public final class Fail extends Node {
    private final Failure result;

    public Fail(FailureCode code, String reason) {
        super("fail");
        result = new Failure(code, reason);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return result;
    }
}
