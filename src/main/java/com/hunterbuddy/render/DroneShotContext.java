package com.hunterbuddy.render;

import net.minecraft.util.math.Vec3d;

public record DroneShotContext(Vec3d focus, Vec3d level, double side, double age, double orbitAngle,
                               double distance, Vec3d anchor, double surface, double duration) {
    public Vec3d right() {
        return new Vec3d(-level.z, 0.0, level.x);
    }
}
