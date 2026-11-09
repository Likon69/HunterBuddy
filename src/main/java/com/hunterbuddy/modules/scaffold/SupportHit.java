package com.hunterbuddy.modules.scaffold;

import com.hunterbuddy.modules.regear.util.PlacementUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

final class SupportHit {
    private SupportHit() {}

    static double reach(ScaffoldSettings settings) {
        return Math.min(settings.closestBlockRange.get(), MinecraftClient.getInstance().player.getBlockInteractionRange());
    }

    static boolean usable(BlockPos position) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return position.getY() >= mc.world.getBottomY() && position.getY() <= mc.world.getTopYInclusive()
            && mc.world.getWorldBorder().contains(position) && BlockUtils.canPlace(position, true);
    }

    static BlockHitResult find(BlockPos target, Vec3d eye, double reach) {
        MinecraftClient mc = MinecraftClient.getInstance();
        BlockHitResult best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction side : Direction.values()) {
            BlockPos neighbour = target.offset(side);
            BlockState state = mc.world.getBlockState(neighbour);
            if (state.isReplaceable() || state.getCollisionShape(mc.world, neighbour).isEmpty()) continue;
            VoxelShape shape = state.getOutlineShape(mc.world, neighbour);
            for (Box box : shape.getBoundingBoxes()) {
                Direction face = side.getOpposite();
                Vec3d point = point(box.offset(neighbour), face, eye);
                BlockHitResult hit = new BlockHitResult(point, face, neighbour, false);
                double distance = point.squaredDistanceTo(eye);
                if (distance < bestDistance && visible(hit, eye, reach)) {
                    best = hit;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    static BlockHitResult air(BlockPos target, Vec3d eye, double reach) {
        BlockHitResult hit = PlacementUtils.getAirPlaceHit(target, reach);
        return hit != null && visible(hit, eye, reach) ? hit : null;
    }

    static Vec3d point(Box box, Direction face, Vec3d eye) {
        double x = inset(eye.x, box.minX, box.maxX);
        double y = box.maxY - Math.min(0.05, (box.maxY - box.minY) * 0.25);
        double z = inset(eye.z, box.minZ, box.maxZ);
        return switch (face) {
            case DOWN -> new Vec3d(x, box.minY, z);
            case UP -> new Vec3d(x, box.maxY, z);
            case NORTH -> new Vec3d(x, y, box.minZ);
            case SOUTH -> new Vec3d(x, y, box.maxZ);
            case WEST -> new Vec3d(box.minX, y, z);
            case EAST -> new Vec3d(box.maxX, y, z);
        };
    }

    static boolean inFrontAndReach(BlockHitResult hit, Vec3d eye, double reach) {
        Vec3d normal = Vec3d.of(hit.getSide().getVector());
        return eye.subtract(hit.getPos()).dotProduct(normal) > 0.000001
            && hit.getPos().squaredDistanceTo(eye) <= reach * reach;
    }

    static boolean visible(BlockHitResult hit, Vec3d eye, double reach) {
        if (!inFrontAndReach(hit, eye, reach)) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d end = hit.isInsideBlock() ? hit.getPos() : hit.getPos().subtract(Vec3d.of(hit.getSide().getVector()).multiply(0.001));
        BlockHitResult traced = mc.world.raycast(new RaycastContext(eye, end,
            RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.isInsideBlock()) {
            return traced.getType() == HitResult.Type.MISS || traced.getBlockPos().equals(hit.getBlockPos());
        }
        return traced.getType() == HitResult.Type.BLOCK && traced.getBlockPos().equals(hit.getBlockPos())
            && traced.getSide() == hit.getSide() && traced.getPos().squaredDistanceTo(hit.getPos()) < 0.0001;
    }

    private static double inset(double value, double min, double max) {
        double inset = Math.min(0.05, (max - min) * 0.25);
        return MathHelper.clamp(value, min + inset, max - inset);
    }
}
