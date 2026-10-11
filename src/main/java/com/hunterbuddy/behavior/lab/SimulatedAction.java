package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public final class SimulatedAction extends Action {
    private final Function<TickContext, NodeResult> work;
    private final List<ExitReason> stops = new ArrayList<>();
    private int starts;
    private int ticks;
    private int cleanups;

    public SimulatedAction(String name) {
        this(name, ctx -> Running.WORKING);
    }

    public SimulatedAction(String name, Function<TickContext, NodeResult> work) {
        super(name);
        this.work = Objects.requireNonNull(work);
    }

    @Override
    protected void onActionStart(TickContext ctx) {
        starts++;
        activation().defer(() -> cleanups++);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        ticks++;
        return work.apply(ctx);
    }

    @Override
    protected void onActionStop(TickContext ctx, ExitReason reason) {
        stops.add(reason);
    }

    public int starts() {
        return starts;
    }

    public int ticks() {
        return ticks;
    }

    public int cleanups() {
        return cleanups;
    }

    public List<ExitReason> stops() {
        return List.copyOf(stops);
    }
}
