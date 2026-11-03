package com.hunterbuddy.modules.scaffold;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

final class SupportHit {
    private static final double HALF_BLOCK = 0.5;

    private SupportHit() {
    }

    static BlockHitResult find(BlockPos target, Vec3d eye, double reach) {
        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction side : Direction.values()) {
            BlockHitResult hit = hitOn(target.offset(side), side.getOpposite(), eye, reach);
            if (hit == null) continue;
            double distance = hit.getPos().squaredDistanceTo(eye);
            if (distance < bestDistance) {
                best = hit;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static BlockHitResult hitOn(BlockPos neighbour, Direction face, Vec3d eye, double reach) {
        if (!isSolid(neighbour)) return null;
        Vec3d normal = Vec3d.of(face.getVector());
        Vec3d point = Vec3d.ofCenter(neighbour).add(normal.multiply(HALF_BLOCK));
        boolean facing = eye.subtract(point).dotProduct(normal) > 0.0;
        boolean inReach = point.squaredDistanceTo(eye) <= reach * reach;
        return facing && inReach ? new BlockHitResult(point, face, neighbour, false) : null;
    }

    private static boolean isSolid(BlockPos position) {
        MinecraftClient mc = MinecraftClient.getInstance();
        BlockState state = mc.world.getBlockState(position);
        return !state.isReplaceable() && !state.getCollisionShape(mc.world, position).isEmpty();
    }
}
