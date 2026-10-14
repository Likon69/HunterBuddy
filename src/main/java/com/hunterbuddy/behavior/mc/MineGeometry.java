package com.hunterbuddy.behavior.mc;

import net.minecraft.block.FallingBlock;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class MineGeometry {
    private MineGeometry() {}

    public static boolean clear(WorldReader world, BlockPos point) {
        if (!world.inBounds(point) || !world.loaded(point)) return false;
        return world.state(point).isAir() || world.state(point).getFluidState().isIn(FluidTags.WATER) && world.cleared(point);
    }

    public static boolean floor(WorldReader world, BlockPos point) {
        if (!world.inBounds(point) || !world.loaded(point)) return false;
        if (!world.state(point).isSideSolidFullSquare(world.client().world, point, Direction.UP)) return false;
        BlockPos below = point;
        while (world.state(below).getBlock() instanceof FallingBlock) {
            below = below.down();
            if (!world.inBounds(below) || !world.loaded(below)
                || !world.state(below).isSideSolidFullSquare(world.client().world, below, Direction.UP)) return false;
        }
        return true;
    }

    public static boolean standing(WorldReader world, BlockPos feet) {
        return clear(world, feet) && clear(world, feet.up()) && floor(world, feet.down());
    }

    public static boolean swimmable(WorldReader world, BlockPos feet) {
        return clear(world, feet) && clear(world, feet.up())
            && (world.state(feet).getFluidState().isIn(FluidTags.WATER) || world.state(feet.up()).getFluidState().isIn(FluidTags.WATER));
    }

    public static boolean lavaAdjacent(WorldReader world, BlockPos point) {
        if (world.loaded(point) && world.state(point).getFluidState().isIn(FluidTags.LAVA)) return true;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = point.offset(direction);
            if (world.loaded(neighbor) && world.state(neighbor).getFluidState().isIn(FluidTags.LAVA)) return true;
        }
        return false;
    }

    public static boolean fallingAbove(WorldReader world, BlockPos point) {
        BlockPos above = point.up();
        return !world.loaded(above) || world.state(above).getBlock() instanceof FallingBlock;
    }
}
