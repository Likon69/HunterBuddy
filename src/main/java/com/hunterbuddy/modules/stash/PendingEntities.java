package com.hunterbuddy.modules.stash;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class PendingEntities {
    private static final int PEARL_WAIT_TICKS = 40;
    private static final int FRAME_WAIT_TICKS = 100;
    private static final double PEARL_MAX_MOVEMENT_SQUARED = 0.25;

    public record Detection(StashType type, ChunkPos chunk, int count) {
    }

    private enum Kind {
        PEARL,
        FRAME
    }

    private record Waiting(int id, int queuedAt, double x, double y, double z, Kind kind) {
    }

    private final FrameClassifier frames;
    private final List<Waiting> waiting = new ArrayList<>();
    private final Set<Integer> settledPearls = new HashSet<>();
    private int tick;

    public PendingEntities(FrameClassifier frames) {
        this.frames = frames;
    }

    public void clear() {
        waiting.clear();
        settledPearls.clear();
    }

    public void queuePearl(Entity pearl) {
        if (settledPearls.contains(pearl.getId()) || isWaiting(pearl.getId())) return;
        waiting.add(new Waiting(pearl.getId(), tick, pearl.getX(), pearl.getY(), pearl.getZ(), Kind.PEARL));
    }

    public void queueFrame(ItemFrameEntity frame) {
        if (isWaiting(frame.getId())) return;
        waiting.add(new Waiting(frame.getId(), tick, frame.getX(), frame.getY(), frame.getZ(), Kind.FRAME));
    }

    public List<Detection> advance(ClientWorld world) {
        tick++;
        List<Waiting> resolvedPearls = new ArrayList<>();
        List<ItemFrameEntity> resolvedFrames = new ArrayList<>();
        Iterator<Waiting> iterator = waiting.iterator();
        while (iterator.hasNext()) {
            Waiting item = iterator.next();
            Entity entity = world.getEntityById(item.id());
            if (entity == null || entity.isRemoved()) {
                iterator.remove();
            } else if (resolve(item, entity, resolvedPearls, resolvedFrames)) {
                iterator.remove();
            }
        }
        return countDetections(world, resolvedPearls, resolvedFrames);
    }

    private boolean isWaiting(int id) {
        return waiting.stream().anyMatch(item -> item.id() == id);
    }

    private boolean resolve(Waiting item, Entity entity, List<Waiting> pearls, List<ItemFrameEntity> frameList) {
        if (item.kind() == Kind.PEARL) return resolvePearl(item, entity, pearls);
        return resolveFrame(item, (ItemFrameEntity) entity, frameList);
    }

    private boolean resolvePearl(Waiting item, Entity pearl, List<Waiting> pearls) {
        if (tick - item.queuedAt() < PEARL_WAIT_TICKS) return false;
        double dx = pearl.getX() - item.x();
        double dy = pearl.getY() - item.y();
        double dz = pearl.getZ() - item.z();
        if (dx * dx + dy * dy + dz * dz < PEARL_MAX_MOVEMENT_SQUARED) {
            settledPearls.add(item.id());
            pearls.add(item);
        }
        return true;
    }

    private boolean resolveFrame(Waiting item, ItemFrameEntity frame, List<ItemFrameEntity> frameList) {
        if (!frame.getHeldItemStack().isEmpty()) {
            frameList.add(frame);
            return true;
        }
        return tick - item.queuedAt() > FRAME_WAIT_TICKS;
    }

    private List<Detection> countDetections(ClientWorld world, List<Waiting> pearls, List<ItemFrameEntity> frameList) {
        List<Detection> detections = new ArrayList<>();
        for (Waiting pearl : pearls) {
            Entity entity = world.getEntityById(pearl.id());
            ChunkPos chunk = entity.getChunkPos();
            detections.add(new Detection(
                StashType.ENDER_PEARL, chunk, EntityCensus.settledPearlsInChunk(world, chunk, settledPearls)));
        }
        for (ItemFrameEntity frame : frameList) {
            StashType type = frames.classify(frame);
            if (type == null) continue;
            ChunkPos chunk = frame.getChunkPos();
            int count = EntityCensus.framesInChunk(world, chunk, other -> frames.classify(other) == type);
            detections.add(new Detection(type, chunk, count));
        }
        return detections;
    }
}
