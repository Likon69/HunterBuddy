package com.hunterbuddy.modules.caveair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

final class ChunkScanner {
    private static final int CHUNK_SIZE = 16;
    private static final Direction[][] OPPOSITE_PAIRS = {
        {Direction.UP, Direction.DOWN}, {Direction.NORTH, Direction.SOUTH}, {Direction.EAST, Direction.WEST}
    };

    private final CaveAirOptions options;
    private final PortalClusterFinder clusterFinder;
    private final GreedyMesher mesher = new GreedyMesher();

    ChunkScanner(CaveAirOptions options) {
        this.options = options;
        this.clusterFinder = new PortalClusterFinder(options);
    }

    ChunkResult scan(World world, WorldChunk chunk) {
        Sweep sweep = new Sweep(world, chunk);
        for (int dx = 0; dx < CHUNK_SIZE; dx++) {
            for (int dz = 0; dz < CHUNK_SIZE; dz++) {
                sweep.column(chunk.getPos().getStartX() + dx, chunk.getPos().getStartZ() + dz);
            }
        }
        return toResult(chunk, sweep);
    }

    private ChunkResult toResult(WorldChunk chunk, Sweep sweep) {
        List<CellBox> portals = clusterFinder.find(sweep.disturbances);
        List<CellBox> boxes = mesher.mesh(sweep.caveAir);
        return new ChunkResult(chunk.getPos().x, chunk.getPos().z, portals, boxes, sweep.enclosedAir);
    }

    private final class Sweep {
        final Set<BlockPos> disturbances = new HashSet<>();
        final List<BlockPos> caveAir = new ArrayList<>();
        final List<BlockPos> enclosedAir = new ArrayList<>();
        private final World world;
        private final WorldChunk chunk;
        private final int bottom;
        private final int top;

        Sweep(World world, WorldChunk chunk) {
            this.world = world;
            this.chunk = chunk;
            this.bottom = Math.max(options.minY.get(), world.getBottomY());
            this.top = Math.min(options.maxY.get(), world.getTopYInclusive());
        }

        void column(int x, int z) {
            for (int y = bottom; y <= top; y++) {
                visit(new BlockPos(x, y, z));
            }
        }

        private void visit(BlockPos pos) {
            BlockState state = chunk.getBlockState(pos);
            if (state.isOf(Blocks.CAVE_AIR) && options.showAllCaveAir.get()) {
                caveAir.add(pos);
            } else if (state.isOf(Blocks.AIR)) {
                classifyAir(pos);
            }
        }

        private void classifyAir(BlockPos pos) {
            if (countCaveAirNeighbours(pos) >= options.minCaveAirNeighbors.get()) {
                disturbances.add(pos);
            }
            if (options.highlightAllAir.get() && isEnclosedByCaveAir(pos)) {
                enclosedAir.add(pos);
            }
        }

        private int countCaveAirNeighbours(BlockPos pos) {
            int count = 0;
            for (Direction direction : Direction.values()) {
                if (isCaveAir(pos.offset(direction))) {
                    count++;
                }
            }
            return count;
        }

        private boolean isEnclosedByCaveAir(BlockPos pos) {
            for (Direction[] pair : OPPOSITE_PAIRS) {
                if (isCaveAir(pos.offset(pair[0])) && isCaveAir(pos.offset(pair[1]))) return true;
            }
            return false;
        }

        private boolean isCaveAir(BlockPos pos) {
            try {
                return world.getBlockState(pos).isOf(Blocks.CAVE_AIR);
            } catch (RuntimeException e) {
                return false;
            }
        }
    }
}
