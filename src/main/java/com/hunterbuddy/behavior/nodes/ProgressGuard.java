package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressEvidence;
import com.hunterbuddy.behavior.core.ProgressSensor;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;

public final class ProgressGuard extends Decorator {
    private final ProgressSensor sensor;
    private final double minGain;
    private final int window;
    private double best;
    private int inactive;

    public ProgressGuard(ProgressSensor sensor, double minGain, int windowTicks, Node child) {
        super("progress-guard", child);
        if (!Double.isFinite(minGain) || minGain <= 0 || windowTicks < 1) throw new IllegalArgumentException("invalid progress window");
        this.sensor = Objects.requireNonNull(sensor);
        this.minGain = minGain;
        window = windowTicks;
    }

    @Override
    protected void onStart(TickContext ctx) {
        best = sensor.value(ctx.sensors());
        inactive = 0;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        double value = sensor.value(ctx.sensors());
        double gain = value - best;
        if (Double.isFinite(gain) && gain >= minGain && ctx.progress(new ProgressEvidence(EvidenceKind.GOAL_BEST, gain, "goal best"))) {
            best = value;
            inactive = 0;
        } else if (++inactive >= window) {
            child().halt(ctx, ExitReason.ABORTED);
            return fail(FailureCode.NO_PROGRESS, "window=" + window);
        }
        return child().execute(ctx);
    }
}
