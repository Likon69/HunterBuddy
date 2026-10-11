package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.Blackboard;
import com.hunterbuddy.behavior.core.Clock;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressPolicy;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.core.TreeRoot;
import com.hunterbuddy.behavior.resource.ResourceArbiter;
import com.hunterbuddy.behavior.trace.RingSink;
import com.hunterbuddy.behavior.trace.Trace;

public final class Simulation {
    private long time;
    private final Clock clock = new Clock(() -> time);
    private final RingSink ring = new RingSink();
    private final TickContext context;

    public Simulation() {
        this(ProgressPolicy.DEFAULT);
    }

    public Simulation(ProgressPolicy policy) {
        context = new TickContext(clock, new Blackboard(), new ResourceArbiter(), new Trace(ring), policy);
    }

    public void advance(long ms) {
        time += ms;
        clock.advance();
    }

    public NodeResult tick(Node node, Sensors sensors) {
        advance(50);
        context.begin(sensors);
        clock.countActive();
        context.monitor().sample(clock, sensors);
        try {
            return node.execute(context);
        } finally {
            context.end();
        }
    }

    public NodeResult tick(Node node) {
        return tick(node, SimulatedSensors.idle());
    }

    public void tick(TreeRoot root, Sensors sensors) {
        advance(50);
        root.tick(sensors);
    }

    public TickContext ctx() {
        return context;
    }

    public RingSink ring() {
        return ring;
    }
}
