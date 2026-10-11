package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;

public final class Delay extends Node {
    private final int ticks;
    private int elapsed;

    public Delay(int ticks) {
        super("delay(" + ticks + ")");
        if (ticks < 0) throw new IllegalArgumentException("negative delay");
        this.ticks = ticks;
    }

    @Override
    protected void onStart(TickContext ctx) {
        elapsed = 0;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        return elapsed++ >= ticks ? Success.INSTANCE : new Running("waiting");
    }
}
