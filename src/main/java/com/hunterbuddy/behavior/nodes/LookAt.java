package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.Conditions;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.Objects;
import java.util.function.Function;

public final class LookAt extends Action {
    private final Function<TickContext, LookIntent> target;
    private final float turnRate;
    private final Outputs outputs;

    public LookAt(Key<TargetPoint> targetKey, double turnRate, WorldReader world, Outputs outputs) {
        this(pointTarget(targetKey, world), turnRate, outputs);
    }

    private LookAt(Function<TickContext, LookIntent> target, double turnRate, Outputs outputs) {
        super("look-at");
        if (!Double.isFinite(turnRate) || turnRate <= 0 || turnRate > 360) throw new IllegalArgumentException("invalid turn rate");
        this.target = Objects.requireNonNull(target);
        this.turnRate = (float) turnRate;
        if (this.turnRate <= 0) throw new IllegalArgumentException("turn rate is below float precision");
        this.outputs = Objects.requireNonNull(outputs);
    }

    private static Function<TickContext, LookIntent> pointTarget(Key<TargetPoint> targetKey, WorldReader world) {
        Objects.requireNonNull(targetKey);
        Objects.requireNonNull(world);
        return ctx -> Conditions.towards(world.eyes(), ctx.bb().get(ctx.owner(), targetKey).orElseThrow());
    }

    public static LookAt angles(Key<LookIntent> targetKey, double turnRate, Outputs outputs) {
        Objects.requireNonNull(targetKey);
        return new LookAt(ctx -> ctx.bb().get(ctx.owner(), targetKey).orElseThrow(), turnRate, outputs);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        LookIntent desired = target.apply(ctx);
        if (Conditions.facing(ctx.sensors(), desired)) return Success.INSTANCE;
        float currentYaw = ctx.sensors().yaw();
        float yaw = currentYaw + Math.clamp(Conditions.wrap(desired.yaw() - currentYaw), -turnRate, turnRate);
        float pitch = ctx.sensors().pitch() + Math.clamp(desired.pitch() - ctx.sensors().pitch(), -turnRate, turnRate);
        outputs.look(ctx.owner(), new LookIntent(yaw, pitch));
        return new Running("visible look towards yaw=" + desired.yaw() + " pitch=" + desired.pitch());
    }
}
