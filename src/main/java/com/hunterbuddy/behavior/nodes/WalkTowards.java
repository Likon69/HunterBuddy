package com.hunterbuddy.behavior.nodes;

import com.hunterbuddy.behavior.core.Action;
import com.hunterbuddy.behavior.core.FailureCode;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Running;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.Success;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.mc.Conditions;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.LookIntent;
import com.hunterbuddy.behavior.resource.MovementIntent;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.Objects;

public final class WalkTowards extends Action {
    private final Key<TargetPoint> targetKey;
    private final double radius;
    private final WorldReader world;
    private final Outputs outputs;

    public WalkTowards(Key<TargetPoint> targetKey, double radius, WorldReader world, Outputs outputs) {
        super("walk-towards");
        if (!Double.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("invalid arrival radius");
        this.targetKey = Objects.requireNonNull(targetKey);
        this.radius = radius;
        this.world = Objects.requireNonNull(world);
        this.outputs = Objects.requireNonNull(outputs);
    }

    @Override
    protected NodeResult onActionTick(TickContext ctx) {
        TargetPoint target = ctx.bb().get(ctx.owner(), targetKey).orElse(null);
        if (target == null) return fail(FailureCode.NO_OPTION, "walking target is missing");
        if (!world.available()) return fail(FailureCode.REFUSED, "Minecraft world is unavailable");
        if (!ctx.sensors().baritoneAvailable()) return fail(FailureCode.UNSUPPORTED, "Baritone movement is unavailable");
        if (!world.loaded(target)) return fail(FailureCode.UNREACHABLE, "target chunk is not loaded");
        Sensors sensors = ctx.sensors();
        double distance = Conditions.distance(sensors, target);
        ctx.goalDistance(target.toString(), distance);
        if (Conditions.near(sensors, target, radius)) {
            if (ctx.monitor().samplesValid(ctx.tick())) return Success.INSTANCE;
            return new Running("arrival sample rejected=setback");
        }
        double dx = target.x() - sensors.x();
        double dz = target.z() - sensors.z();
        boolean swimming = sensors.inWater() || sensors.submerged();
        float yaw = dx == 0 && dz == 0 ? sensors.yaw()
            : sensors.yaw() + Conditions.wrap((float) Math.toDegrees(Math.atan2(-dx, dz)) - sensors.yaw());
        float pitch = swimming ? Conditions.towards(new TargetPoint(sensors.x(), sensors.y(), sensors.z()), target).pitch() : 0;
        outputs.look(ctx.owner(), new LookIntent(yaw, pitch));
        outputs.movement(ctx.owner(), new MovementIntent(dx != 0 || dz != 0, false, false, false,
            swimming || sensors.horizontalCollision(), false, swimming && sensors.food() > 6));
        return new Running("walking distance=" + distance + " radius=" + radius);
    }
}
