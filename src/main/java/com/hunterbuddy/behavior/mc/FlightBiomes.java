package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.TickContext;
import com.hunterbuddy.behavior.farmer.BiomeObservation;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

public final class FlightBiomes {
    private final WorldReader world;
    private final WorldKnowledge memory;
    private final Map<WorldChunk, Long> sampled = new WeakHashMap<>();
    private long savedAt;
    private boolean dirty;

    public FlightBiomes(WorldReader world, TickContext ctx) {
        this.world = world;
        memory = WorldKnowledge.load(ctx);
        savedAt = ctx.tick();
    }

    public void sample(TickContext ctx) {
        if (!ctx.sensors().gliding()) return;
        long now = System.currentTimeMillis();
        for (WorldChunk chunk : LoadedChunks.read(world)) {
            if (!memory.acceptsBiome(chunk.getPos())) continue;
            Long previous = sampled.get(chunk);
            if (previous != null && ctx.tick() - previous < 200) continue;
            int x = chunk.getPos().getStartX() + 8;
            int z = chunk.getPos().getStartZ() + 8;
            int y = Math.clamp(chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, 8, 8), world.bottomY(), world.topY());
            BlockPos point = new BlockPos(x, y, z);
            String biome = world.client().world.getBiome(point).getIdAsString();
            memory.biomes.put(ZoneCoverage.key(chunk.getPos().x, chunk.getPos().z), new BiomeObservation(biome, now));
            sampled.put(chunk, ctx.tick());
            dirty = true;
        }
        if (ctx.tick() - savedAt >= 1200) {
            if (dirty) memory.save(ctx);
            savedAt = ctx.tick();
            dirty = false;
        }
    }

    public void save(TickContext ctx) {
        memory.saveNow(ctx);
        savedAt = ctx.tick();
        dirty = false;
    }
}
