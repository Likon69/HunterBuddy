package com.hunterbuddy.modules.flowesp;

import java.util.List;
import net.minecraft.util.math.ChunkPos;

record FlowChunk(ChunkPos pos, FluidCounts counts, List<FlowColumn> columns, long firstSeenMs) {
    record FluidCounts(int sources, int flows) {
        private static final int CAP = Short.MAX_VALUE;

        FluidCounts {
            sources = Math.min(CAP, sources);
            flows = Math.min(CAP, flows);
        }
    }

    int total() {
        return counts.sources() + counts.flows();
    }

    double flowRatio() {
        return (double) counts.flows() / (counts.sources() + 1);
    }

    boolean hasRoofColumn() {
        return columns.stream().anyMatch(column -> column.flags().roof());
    }
}
