package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.EvidenceKind;
import com.hunterbuddy.behavior.core.ExitReason;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.ProgressEvidence;
import com.hunterbuddy.behavior.core.ProgressSensor;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;
import java.util.function.Function;

public final class ProgressGuard extends Decorator {
    private final ProgressSensor sensor;
    private final double minGain;
    private final int window;
    private int inactive;
    private String activeKey;
    private boolean retained;
    private boolean retainOnSuccess;
    private Function<TickContext, String> target = ctx -> path();

    public ProgressGuard(ProgressSensor sensor, double minGain, int windowTicks, Node child) {
        super("progress-guard", child);
        if (!Double.isFinite(minGain) || minGain <= 0 || windowTicks < 1) throw new IllegalArgumentException("invalid progress window");
        this.sensor = Objects.requireNonNull(sensor);
        this.minGain = minGain;
        window = windowTicks;
    }

    @Override
    protected void onStart(TickContext ctx) {
        String key = Objects.requireNonNull(target.apply(ctx));
        if (!retained || !key.equals(activeKey)) {
            inactive = 0;
            ctx.bb().remove(ctx.owner(), new Key<>("progress-best/" + key, Double.class, Scope.OWNER, false));
        }
        activeKey = key;
        retained = false;
    }

    public ProgressGuard keyedBy(Function<TickContext, String> target) {
        if (active()) throw new IllegalStateException("cannot configure an active ProgressGuard");
        this.target = Objects.requireNonNull(target);
        return this;
    }

    ProgressGuard retainOnSuccess() {
        if (active()) throw new IllegalStateException("cannot configure an active ProgressGuard");
        retainOnSuccess = true;
        return this;
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        double value = sensor.value(ctx.sensors());
        Key<Double> key = new Key<>("progress-best/" + activeKey, Double.class, Scope.OWNER, false);
        Double best = ctx.bb().get(ctx.owner(), key).orElse(null);
        if (best == null && Double.isFinite(value) && ctx.monitor().samplesValid(ctx.tick())) {
            best = value;
            ctx.bb().set(ctx.owner(), key, best);
        }
        double gain = best == null ? 0 : value - best;
        if (Double.isFinite(gain) && gain >= minGain && ctx.progress(new ProgressEvidence(EvidenceKind.GOAL_BEST, gain, "goal best"))) {
            ctx.bb().set(ctx.owner(), key, value);
            inactive = 0;
        } else if (++inactive >= window) {
            child().halt(ctx, ExitReason.ABORTED);
            return fail(FailureCode.NO_PROGRESS, "window=" + window);
        }
        return child().execute(ctx);
    }

    @Override
    protected void onStop(TickContext ctx, ExitReason reason) {
        retained = reason == ExitReason.PAUSED || reason == ExitReason.PREEMPTED
            || retainOnSuccess && reason == ExitReason.COMPLETED;
    }

    @Override
    protected void onReset() {
        inactive = 0;
        activeKey = null;
        retained = false;
    }
}
