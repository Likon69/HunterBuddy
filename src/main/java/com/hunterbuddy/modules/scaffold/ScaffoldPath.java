package com.hunterbuddy.modules.scaffold;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

record ScaffoldPath(Vec3d feet, Vec3d direction) {
    static ScaffoldPath create(Vec3d position, Vec3d velocity, Vec3d input, boolean predict) {
        Vec3d motion = new Vec3d(velocity.x, 0, velocity.z);
        Vec3d direction = motion.lengthSquared() > 0.000001 ? motion.normalize() : input.normalize();
        return new ScaffoldPath(predict ? position.add(velocity) : position, direction);
    }

    BlockPos at(double distance) {
        return BlockPos.ofFloored(feet.x + direction.x * distance, feet.y - 0.01, feet.z + direction.z * distance);
    }
}
