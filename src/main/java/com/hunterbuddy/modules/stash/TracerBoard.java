package com.hunterbuddy.modules.stash;

import com.hunterbuddy.modules.StashFinder.StashChunk;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TracerBoard {
    private static final double DEFAULT_HEIGHT = 64.0;

    private final Map<StashChunk, Vec3d> tracers = new ConcurrentHashMap<>();

    public Map<StashChunk, Vec3d> all() {
        return tracers;
    }

    public boolean isEmpty() {
        return tracers.isEmpty();
    }

    public boolean has(StashChunk chunk) {
        return tracers.containsKey(chunk);
    }

    public void markAtEyeHeight(StashChunk chunk, ClientPlayerEntity player) {
        tracers.put(chunk, new Vec3d(chunk.x, player.getEyeY(), chunk.z));
    }

    public void toggle(StashChunk chunk, ClientPlayerEntity player) {
        if (has(chunk)) {
            tracers.remove(chunk);
            return;
        }
        double height = player == null ? DEFAULT_HEIGHT : player.getEyeY();
        tracers.put(chunk, new Vec3d(chunk.x, height, chunk.z));
    }

    public void remove(StashChunk chunk) {
        tracers.remove(chunk);
    }

    public void clear() {
        tracers.clear();
    }

    public void removeNear(Vec3d viewer, double distance) {
        tracers.values().removeIf(point -> horizontalDistance(viewer, point) <= distance);
    }

    public static double horizontalDistance(Vec3d from, Vec3d to) {
        return Math.hypot(from.x - to.x, from.z - to.z);
    }
}
