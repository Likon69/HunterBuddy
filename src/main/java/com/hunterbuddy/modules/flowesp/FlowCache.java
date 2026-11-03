package com.hunterbuddy.modules.flowesp;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

final class FlowCache {
    private final DimensionCache overworld;
    private final DimensionCache nether;
    private final Set<Long> inProgress = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean dirty = new AtomicBoolean();

    FlowCache(int capacity) {
        overworld = new DimensionCache(capacity);
        nether = new DimensionCache(capacity);
    }

    DimensionCache overworld() {
        return overworld;
    }

    DimensionCache nether() {
        return nether;
    }

    DimensionCache of(RegistryKey<World> dimension) {
        return dimension == World.NETHER ? nether : overworld;
    }

    boolean beginScan(long chunkKey) {
        return inProgress.add(chunkKey);
    }

    void endScan(long chunkKey) {
        inProgress.remove(chunkKey);
    }

    void markDirty() {
        dirty.set(true);
    }

    boolean consumeDirty() {
        return dirty.getAndSet(false);
    }

    void clear() {
        overworld.clear();
        nether.clear();
        inProgress.clear();
    }
}
