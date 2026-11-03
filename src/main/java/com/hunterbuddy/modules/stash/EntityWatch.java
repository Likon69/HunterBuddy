package com.hunterbuddy.modules.stash;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;

public final class EntityWatch {
    private static final int SWEEP_INTERVAL_TICKS = 20;

    private final StashDetectionSettings detection;
    private final StashThresholdSettings thresholds;
    private final EntityDetections detections;
    private final PendingEntities pending;
    private int sweepCountdown;

    public EntityWatch(
        StashDetectionSettings detection,
        StashThresholdSettings thresholds,
        EntityDetections detections,
        PendingEntities pending
    ) {
        this.detection = detection;
        this.thresholds = thresholds;
        this.detections = detections;
        this.pending = pending;
    }

    public void clear() {
        pending.clear();
    }

    public void onEntityAdded(Entity entity) {
        ClientWorld world = MeteorClient.mc.world;
        if (MeteorClient.mc.player == null || world == null) return;
        if (entity instanceof EnderPearlEntity && detection.detects(StashType.ENDER_PEARL)) {
            pending.queuePearl(entity);
        } else if (entity instanceof StorageMinecartEntity && detection.detects(StashType.STACKED_MINECART)) {
            recordMinecartStack(world, entity);
        } else if (EntityCensus.isNamedLiving(entity) && detection.detects(StashType.NAMED_ENTITY)) {
            detections.record(entity.getChunkPos(), StashType.NAMED_ENTITY,
                EntityCensus.namedInChunk(world, entity.getChunkPos()));
        } else if (entity instanceof ItemFrameEntity frame && detectsFrames()) {
            pending.queueFrame(frame);
        }
    }

    public void tickPending() {
        ClientWorld world = MeteorClient.mc.world;
        if (world == null) return;
        for (PendingEntities.Detection ready : pending.advance(world)) {
            detections.record(ready.chunk(), ready.type(), ready.count());
        }
    }

    public void tickSweep() {
        if (sweepCountdown > 0) {
            sweepCountdown--;
            return;
        }
        sweepCountdown = SWEEP_INTERVAL_TICKS;
        sweepMinecartPiles();
    }

    private boolean detectsFrames() {
        return detection.detects(StashType.MAP_ITEM_FRAME) || detection.detects(StashType.ITEM_FRAME);
    }

    private void recordMinecartStack(ClientWorld world, Entity cart) {
        int depth = EntityCensus.minecartsInBlock(world, cart.getBlockPos());
        if (depth < thresholds.minimum(StashType.STACKED_MINECART)) return;
        detections.record(cart.getChunkPos(), StashType.STACKED_MINECART, depth);
    }

    private void sweepMinecartPiles() {
        ClientWorld world = MeteorClient.mc.world;
        if (!detection.detects(StashType.STACKED_MINECART) || world == null || MeteorClient.mc.player == null) return;
        int minimum = thresholds.minimum(StashType.STACKED_MINECART);
        for (Map.Entry<BlockPos, Integer> pile : EntityCensus.minecartPiles(world).entrySet()) {
            if (pile.getValue() >= minimum) {
                detections.record(new ChunkPos(pile.getKey()), StashType.STACKED_MINECART, pile.getValue());
            }
        }
    }
}
