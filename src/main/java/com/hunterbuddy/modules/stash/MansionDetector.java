package com.hunterbuddy.modules.stash;

import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.World;

public final class MansionDetector {
    private static final int CHUNK_CENTER = 8;

    private MansionDetector() {
    }

    public static boolean isMansion(WorldChunk chunk) {
        if (!chunk.hasHeightmap(Heightmap.Type.WORLD_SURFACE)) return false;
        int surface = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, CHUNK_CENTER, CHUNK_CENTER);
        return isDarkForest(chunk, surface) && hasMansionWood(chunk);
    }

    private static boolean isDarkForest(WorldChunk chunk, int surface) {
        World world = chunk.getWorld();
        BlockPos center = new BlockPos(
            chunk.getPos().getStartX() + CHUNK_CENTER, surface, chunk.getPos().getStartZ() + CHUNK_CENTER);
        RegistryEntry<Biome> biome = world.getBiome(center);
        return biome.matchesKey(BiomeKeys.DARK_FOREST);
    }

    private static boolean hasMansionWood(WorldChunk chunk) {
        boolean birch = false;
        boolean darkOak = false;
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;
            birch |= section.hasAny(state -> state.isOf(Blocks.BIRCH_PLANKS));
            darkOak |= section.hasAny(state -> state.isOf(Blocks.DARK_OAK_PLANKS) || state.isOf(Blocks.DARK_OAK_LOG));
        }
        return birch && darkOak;
    }
}
