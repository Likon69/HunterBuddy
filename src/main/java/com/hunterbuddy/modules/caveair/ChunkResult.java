package com.hunterbuddy.modules.caveair;

import java.util.List;
import net.minecraft.util.math.BlockPos;

record ChunkResult(int chunkX, int chunkZ, List<CellBox> portals, List<CellBox> caveAirBoxes, List<BlockPos> enclosedAir) {
    boolean isEmpty() {
        return portals.isEmpty() && caveAirBoxes.isEmpty() && enclosedAir.isEmpty();
    }
}
