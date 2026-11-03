package com.hunterbuddy.modules.scaffold;

import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class NearestSolid {
    private NearestSolid() {
    }

    static BlockPos find(ClientPlayerEntity player, double range) {
        World world = MinecraftClient.getInstance().world;
        int minX = (int) Math.floor(player.getX() - range);
        int maxX = (int) (player.getX() + range);
        int minZ = (int) Math.floor(player.getZ() - range);
        int maxZ = (int) (player.getZ() + range);
        int minY = Math.max(world.getBottomY(), (int) (player.getY() - range));
        int maxY = Math.min(world.getTopYInclusive(), (int) (player.getY() + range));
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        BlockPos best = null;
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    cursor.set(x, y, z);
                    best = nearer(best, cursor, world);
                }
            }
        }
        return best;
    }

    private static BlockPos nearer(BlockPos best, BlockPos.Mutable candidate, World world) {
        if (world.getBlockState(candidate).isAir()) return best;
        boolean closer = best == null || PlayerUtils.distanceTo(candidate) < PlayerUtils.distanceTo(best);
        return closer ? candidate.toImmutable() : best;
    }
}
