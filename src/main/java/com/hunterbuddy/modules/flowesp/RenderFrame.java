package com.hunterbuddy.modules.flowesp;

import net.minecraft.util.math.Vec3d;

record RenderFrame(Vec3d viewer, double renderDistanceSquared, Pulse pulse, double fadeStep) {
    record Pulse(double value, double alpha, double size) {
        static final Pulse STEADY = new Pulse(1.0, 0.0, 0.0);
    }

    double distanceRatio(double squaredDistance) {
        return squaredDistance / renderDistanceSquared;
    }

    double alphaFactor(FlowColumn column) {
        return column.flags().tip() ? 1.0 - pulse.alpha() * (1.0 - pulse.value()) : 1.0;
    }

    double expansion(FlowColumn column) {
        return column.flags().tip() ? pulse.size() * pulse.value() : 0.0;
    }
}
