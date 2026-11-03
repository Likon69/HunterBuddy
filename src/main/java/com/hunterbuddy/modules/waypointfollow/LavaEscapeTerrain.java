package com.hunterbuddy.modules.waypointfollow;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class LavaEscapeTerrain {
    private LavaEscapeTerrain() {
    }

    public static Direction directionToward(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    public static boolean isOpen(BlockPos pos) {
        BlockState state = MeteorClient.mc.world.getBlockState(pos);
        return !state.getFluidState().isIn(FluidTags.LAVA)
            && state.getCollisionShape(MeteorClient.mc.world, pos).isEmpty();
    }

    public static boolean isBreakable(BlockPos pos) {
        BlockState state = MeteorClient.mc.world.getBlockState(pos);
        return state.getFluidState().isEmpty()
            && !state.getCollisionShape(MeteorClient.mc.world, pos).isEmpty()
            && state.getHardness(MeteorClient.mc.world, pos) >= 0.0F;
    }
}
