package com.hunterbuddy.behavior.farmer;

import com.hunterbuddy.behavior.core.Decorator;
import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Node;
import com.hunterbuddy.behavior.core.NodeResult;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.core.Sensors;
import com.hunterbuddy.behavior.core.TargetPoint;
import com.hunterbuddy.behavior.core.TickContext;
import java.util.Objects;
import java.util.function.Consumer;

public final class FarmerTracker extends Decorator {
    public static final Key<TargetPoint> LAST_SAFE = new Key<>("farmer-last-safe", TargetPoint.class, Scope.OWNER, true);
    private final Consumer<String> target;

    public FarmerTracker(Node child, Consumer<String> target) {
        super("farmer-tracker", child);
        this.target = Objects.requireNonNull(target);
    }

    @Override
    protected NodeResult onTick(TickContext ctx) {
        Sensors sensors = ctx.sensors();
        if (sensors.onGround() && !sensors.inLava() && !sensors.submerged()) {
            ctx.bb().set(ctx.owner(), LAST_SAFE, new TargetPoint(sensors.x(), sensors.y(), sensors.z()));
        }
        NodeResult result = child().execute(ctx);
        target.accept(ctx.bb().get(ctx.owner(), TickContext.CURRENT_TARGET).orElse("idle"));
        return result;
    }
}
