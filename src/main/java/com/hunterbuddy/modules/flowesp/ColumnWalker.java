package com.hunterbuddy.modules.flowesp;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;

final class ColumnWalker {
    private static final int CHUNK_SIZE = 16;
    private static final int LAKE_NEIGHBOURS = 3;

    private final WorldChunk chunk;
    private final ScanRules rules;
    private final int tipLevel;
    private final List<FlowColumn> columns = new ArrayList<>();
    private int sources;
    private int flows;
    private int localX;
    private int localZ;
    private FluidRun run;

    ColumnWalker(WorldChunk chunk, ScanRules rules, int tipLevel) {
        this.chunk = chunk;
        this.rules = rules;
        this.tipLevel = tipLevel;
    }

    List<FlowColumn> columns() {
        return columns;
    }

    int sources() {
        return sources;
    }

    int flows() {
        return flows;
    }

    void walk(int x, int z) {
        localX = x;
        localZ = z;
        run = null;
        for (int y = rules.minY(); y <= rules.maxY(); y++) {
            visit(y);
        }
        closeRun();
    }

    private void visit(int y) {
        FluidState fluid = fluidAt(localX, y, localZ);
        if (!ScanRules.isLava(fluid) && !ScanRules.isWater(fluid)) {
            closeRun();
        } else if (rules.counts(fluid) && !isLake(y, fluid)) {
            if (run == null) {
                run = new FluidRun(y, ScanRules.isLava(fluid));
            }
            run.add(y, fluid, rules.isRoof(y, fluid));
        }
    }

    private void closeRun() {
        if (run != null && run.hasFlow()) {
            columns.add(run.toColumn(localX, localZ, tipLevel));
            sources += run.sources();
            flows += run.flows();
        }
        run = null;
    }

    private boolean isLake(int y, FluidState fluid) {
        if (!rules.filtersLakes() || !ScanRules.isWater(fluid) || !fluid.isStill()) return false;
        int stillNeighbours = 0;
        for (Direction side : Direction.Type.HORIZONTAL) {
            if (isStillWaterNeighbour(side, y)) {
                stillNeighbours++;
            }
        }
        return stillNeighbours >= LAKE_NEIGHBOURS;
    }

    private boolean isStillWaterNeighbour(Direction side, int y) {
        int x = localX + side.getOffsetX();
        int z = localZ + side.getOffsetZ();
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) return false;
        FluidState neighbour = fluidAt(x, y, z);
        return ScanRules.isWater(neighbour) && neighbour.isStill();
    }

    private FluidState fluidAt(int x, int y, int z) {
        BlockPos pos = new BlockPos(chunk.getPos().getStartX() + x, y, chunk.getPos().getStartZ() + z);
        return chunk.getFluidState(pos);
    }
}
