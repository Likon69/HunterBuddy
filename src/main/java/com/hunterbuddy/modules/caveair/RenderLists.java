package com.hunterbuddy.modules.caveair;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

final class RenderLists {
    private static final int CHUNK_SIZE = 16;
    private static final double CHUNK_PREFILTER_FACTOR = 4.0;
    private static final double BUFFER_FRACTION = 0.1;
    private static final double BUFFER_CONSTANT = 8.0;

    private final CaveAirOptions options;
    private final ResultCache cache;
    private volatile List<CellBox> clusters = List.of();
    private volatile List<BlockPos> enclosedAir = List.of();
    private volatile List<CellBox> caveAirBoxes = List.of();

    RenderLists(CaveAirOptions options, ResultCache cache) {
        this.options = options;
        this.cache = cache;
    }

    List<CellBox> clusters() {
        return clusters;
    }

    List<BlockPos> enclosedAir() {
        return enclosedAir;
    }

    List<CellBox> caveAirBoxes() {
        return caveAirBoxes;
    }

    void clear() {
        clusters = List.of();
        enclosedAir = List.of();
        caveAirBoxes = List.of();
    }

    void rebuildClusters(Vec3d eye) {
        double limit = square(options.renderDistance.get());
        List<CellBox> visible = new ArrayList<>();
        List<BlockPos> enclosed = new ArrayList<>();
        for (ChunkResult result : cache.all()) {
            if (!isChunkNear(result, eye, limit)) continue;
            result.portals().stream().filter(box -> box.distanceSquaredTo(eye) <= limit).forEach(visible::add);
            if (options.highlightAllAir.get()) {
                result.enclosedAir().stream().filter(pos -> Vec3d.ofCenter(pos).squaredDistanceTo(eye) <= limit).forEach(enclosed::add);
            }
        }
        clusters = visible;
        enclosedAir = enclosed;
    }

    void rebuildCaveAir(Vec3d eye) {
        if (!options.showAllCaveAir.get()) {
            caveAirBoxes = List.of();
            return;
        }
        double base = options.caveAirRenderDistance.get();
        double limit = square(base + base * BUFFER_FRACTION + BUFFER_CONSTANT);
        List<CellBox> visible = new ArrayList<>();
        for (ChunkResult result : cache.all()) {
            if (isChunkNear(result, eye, limit)) {
                result.caveAirBoxes().stream().filter(box -> box.distanceSquaredTo(eye) <= limit).forEach(visible::add);
            }
        }
        caveAirBoxes = visible;
    }

    private static boolean isChunkNear(ChunkResult result, Vec3d eye, double limitSquared) {
        double dx = result.chunkX() * CHUNK_SIZE + CHUNK_SIZE / 2.0 - eye.x;
        double dz = result.chunkZ() * CHUNK_SIZE + CHUNK_SIZE / 2.0 - eye.z;
        return dx * dx + dz * dz <= CHUNK_PREFILTER_FACTOR * limitSquared;
    }

    private static double square(double value) {
        return value * value;
    }
}
