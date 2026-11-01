package com.hunterbuddy.render;

import java.util.OptionalDouble;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public final class DroneTerrain {
    private DroneTerrain() {}

    public static double hitDistance(World world, Entity entity, Vec3d from, Vec3d to) {
        HitResult hit = world.raycast(new RaycastContext(
            from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity
        ));

        return hit.getType() == HitResult.Type.MISS ? -1.0 : hit.getPos().distanceTo(from);
    }

    public static OptionalDouble surfaceBelow(World world, Entity entity, Vec3d from, double depth) {
        HitResult hit = world.raycast(new RaycastContext(
            from, from.add(0.0, -depth, 0.0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, entity
        ));

        return hit.getType() == HitResult.Type.MISS ? OptionalDouble.empty() : OptionalDouble.of(hit.getPos().y);
    }

    public static Vec3d clipped(World world, Entity entity, Vec3d from, Vec3d to, double clearance) {
        double hit = hitDistance(world, entity, from, to);
        if (hit < 0.0) return to;

        Vec3d way = to.subtract(from);
        double length = way.length();
        if (length < 1.0e-6) return from;

        return from.add(way.multiply(Math.max(0.0, hit - clearance) / length));
    }
}
