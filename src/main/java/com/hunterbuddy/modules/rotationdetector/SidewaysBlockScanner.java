package com.hunterbuddy.modules.rotationdetector;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

final class SidewaysBlockScanner {
    private static final int CHUNK_SIZE = 16;

    private final RotationDetectorOptions options;
    private final SidewaysBlockMatcher matcher;

    SidewaysBlockScanner(RotationDetectorOptions options) {
        this.options = options;
        this.matcher = new SidewaysBlockMatcher(options);
    }

    Set<BlockPos> scan(WorldChunk chunk, World world) {
        Sweep sweep = new Sweep(chunk, world);
        for (int dx = 0; dx < CHUNK_SIZE; dx++) {
            for (int dz = 0; dz < CHUNK_SIZE; dz++) {
                sweep.column(chunk.getPos().getStartX() + dx, chunk.getPos().getStartZ() + dz);
            }
        }
        return sweep.found;
    }

    private final class Sweep {
        final Set<BlockPos> found = new HashSet<>();
        private final WorldChunk chunk;
        private final World world;
        private final int bottom;
        private final int top;

        Sweep(WorldChunk chunk, World world) {
            this.chunk = chunk;
            this.world = world;
            this.bottom = Math.max(options.minY.get(), world.getBottomY());
            this.top = Math.min(options.maxY.get(), world.getTopYInclusive());
        }

        void column(int x, int z) {
            for (int y = bottom; y <= top; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = chunk.getBlockState(pos);
                if (!state.isAir() && matcher.isHorizontalUnnatural(state) && passesAirFilter(pos)) {
                    found.add(pos);
                }
            }
        }

        private boolean passesAirFilter(BlockPos pos) {
            if (!options.ignoreNearAir.get()) return true;
            for (int offset = 1; offset <= options.airCheckDistance.get(); offset++) {
                if (world.getBlockState(pos.up(offset)).isAir()) return false;
            }
            return true;
        }
    }
}
