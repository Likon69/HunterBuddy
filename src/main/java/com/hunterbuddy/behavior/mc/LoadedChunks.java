package com.hunterbuddy.behavior.mc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;
import meteordevelopment.meteorclient.mixin.ClientChunkManagerAccessor;
import meteordevelopment.meteorclient.mixin.ClientChunkMapAccessor;
import net.minecraft.world.chunk.WorldChunk;

public final class LoadedChunks {
    private LoadedChunks() {}

    public static List<WorldChunk> read(WorldReader world) {
        ClientChunkManagerAccessor manager = (ClientChunkManagerAccessor) world.client().world.getChunkManager();
        ClientChunkMapAccessor map = (ClientChunkMapAccessor) (Object) manager.meteor$getChunks();
        AtomicReferenceArray<WorldChunk> array = map.meteor$getChunks();
        List<WorldChunk> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            WorldChunk chunk = array.get(i);
            if (chunk != null) result.add(chunk);
        }
        return result;
    }

    public static int measuredRadius(WorldReader world) {
        int x = world.feet().getX() >> 4;
        int z = world.feet().getZ() >> 4;
        for (int radius = 1; radius <= 64; radius++) {
            for (int offset = -radius; offset <= radius; offset++) {
                if (!loaded(world, x + offset, z - radius) || !loaded(world, x + offset, z + radius)
                    || !loaded(world, x - radius, z + offset) || !loaded(world, x + radius, z + offset)) return radius - 1;
            }
        }
        return 64;
    }

    private static boolean loaded(WorldReader world, int x, int z) {
        return world.client().world.getChunkManager().isChunkLoaded(x, z);
    }
}
