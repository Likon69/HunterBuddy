package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.RocketAction;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;

public final class SimulatedRocketAction extends RocketAction {
    private int emissions;

    public SimulatedRocketAction(String name) {
        super(name);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        emissions++;
        return Success.INSTANCE;
    }

    public int emissions() {
        return emissions;
    }
}
