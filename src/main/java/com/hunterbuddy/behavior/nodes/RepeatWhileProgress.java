package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressSensor;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.function.Function;

public final class RepeatWhileProgress extends Decorator {
    private final ProgressGuard progress;

    public RepeatWhileProgress(ProgressSensor sensor, double minGain, int windowTicks, Node child) {
        this(new ProgressGuard(sensor, minGain, windowTicks, child));
    }

    private RepeatWhileProgress(ProgressGuard progress) {
        super("repeat-while-progress", progress);
        this.progress = progress;
    }

    public RepeatWhileProgress keyedBy(Function<TickContext, String> target) {
        if (active()) throw new IllegalStateException("cannot configure an active RepeatWhileProgress");
        progress.keyedBy(target);
        return this;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        NodeResult result = progress.execute(ctx);
        if (!(result instanceof Success)) return result;
        progress.child().resetMemory();
        return Running.WORKING;
    }
}
