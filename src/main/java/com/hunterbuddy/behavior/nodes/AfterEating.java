package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.TickContext;

public final class AfterEating extends Decorator {
    public AfterEating(Node child) {
        super("wait-for-meal-before-hotbar-work", child);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return ctx.sensors().eating() ? new Running("waiting for the meal to finish before hotbar work") : child().execute(ctx);
    }
}
