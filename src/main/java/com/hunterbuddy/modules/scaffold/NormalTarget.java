package com.hunterbuddy.modules.scaffold;

import com.hunterbuddy.modules.regear.util.PlacementUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

final class NormalTarget {
    private static final double AIR_PLACE_DROP = 0.5;
    private static final double FALL_PREDICTION = 0.98;
    private static final double FACE_THRESHOLD = 0.5;

    private final ScaffoldSettings settings;
    private BlockPos support = BlockPos.ORIGIN;

    NormalTarget(ScaffoldSettings settings) {
        this.settings = settings;
    }

    void reset() {
        support = BlockPos.ORIGIN;
    }

    BlockPos find() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (settings.airPlace.get()) return airTarget(player);
        BlockPos under = player.getBlockPos().down();
        if (PlacementUtils.getPlaceSide(under) != null) return under;
        return supportedTarget(player);
    }

    void rememberIfSolid(BlockPos target) {
        if (!MinecraftClient.getInstance().world.getBlockState(target).isAir()) support = target.toImmutable();
    }

    private BlockPos airTarget(ClientPlayerEntity player) {
        Vec3d spot = player.getEntityPos().add(player.getVelocity()).add(0.0, -AIR_PLACE_DROP, 0.0);
        return BlockPos.ofFloored(spot);
    }

    private BlockPos supportedTarget(ClientPlayerEntity player) {
        Vec3d predicted = player.getEntityPos().add(0.0, -FALL_PREDICTION, 0.0).add(player.getVelocity());
        if (PlayerUtils.distanceTo(support) > settings.closestBlockRange.get()) {
            BlockPos nearest = NearestSolid.find(player, settings.closestBlockRange.get());
            if (nearest == null) return null;
            support = nearest;
        }
        return support.offset(faceToward(predicted));
    }

    private Direction faceToward(Vec3d predicted) {
        Vec3d offset = predicted.subtract(Vec3d.ofCenter(support));
        if (offset.y < -FACE_THRESHOLD) return Direction.DOWN;
        if (offset.y > FACE_THRESHOLD) return Direction.UP;
        return Direction.getFacing(offset.x, 0.0, offset.z);
    }

    static boolean outsideBuildLimits(BlockPos target) {
        World world = MinecraftClient.getInstance().world;
        return target.getY() < world.getBottomY() || target.getY() > world.getTopYInclusive();
    }
}
