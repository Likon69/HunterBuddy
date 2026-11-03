package com.hunterbuddy.modules.stash;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class EntityCensus {
    private EntityCensus() {
    }

    public static boolean isNamedLiving(Entity entity) {
        return entity instanceof LivingEntity && entity.hasCustomName();
    }

    public static int minecartsInBlock(ClientWorld world, BlockPos position) {
        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof StorageMinecartEntity && entity.getBlockPos().equals(position)) count++;
        }
        return count;
    }

    public static Map<BlockPos, Integer> minecartPiles(ClientWorld world) {
        Map<BlockPos, Integer> piles = new HashMap<>();
        for (Entity entity : world.getEntities()) {
            if (entity instanceof StorageMinecartEntity) piles.merge(entity.getBlockPos(), 1, Integer::sum);
        }
        return piles;
    }

    public static int namedInChunk(ClientWorld world, ChunkPos chunk) {
        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (isNamedLiving(entity) && entity.getChunkPos().equals(chunk)) count++;
        }
        return count;
    }

    public static int framesInChunk(ClientWorld world, ChunkPos chunk, Predicate<ItemFrameEntity> matching) {
        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof ItemFrameEntity frame && frame.getChunkPos().equals(chunk) && matching.test(frame)) {
                count++;
            }
        }
        return count;
    }

    public static int settledPearlsInChunk(ClientWorld world, ChunkPos chunk, Set<Integer> settled) {
        settled.removeIf(id -> world.getEntityById(id) == null);
        int count = 0;
        for (int id : settled) {
            if (world.getEntityById(id).getChunkPos().equals(chunk)) count++;
        }
        return count;
    }
}
