package com.hunterbuddy.modules.regear.util;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class XaeroWaypointManager {
    private static final Map<String, Set<TrackedKey>> TRACKED = new ConcurrentHashMap<>();

    private record TrackedKey(int x, int y, int z, String category) {
        BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    public static boolean isAvailable() {
        return Utils.XAERO_AVAILABLE;
    }

    public static boolean addWaypoint(String category, BlockPos pos, String name, String initials, MapUtil.WpColor color, boolean temporary, @Nullable String dimension) {
        if (!Utils.XAERO_AVAILABLE) return false;
        TrackedKey key = new TrackedKey(pos.getX(), pos.getY(), pos.getZ(), category);
        Set<TrackedKey> keys = TRACKED.computeIfAbsent(category, c -> ConcurrentHashMap.newKeySet());
        if (!keys.add(key)) return false;
        MapUtil.addWaypoint(pos, category + " - " + name, initials, MapUtil.Purpose.Normal, color, temporary, dimension);
        return true;
    }

    public static boolean addWaypoint(String category, BlockPos pos, String name, String initials, MapUtil.WpColor color, boolean temporary) {
        return addWaypoint(category, pos, name, initials, color, temporary, getCurrentDimension());
    }

    public static boolean addWaypoint(String category, Vec3d pos, String name, String initials, MapUtil.WpColor color, boolean temporary) {
        return addWaypoint(category, BlockPos.ofFloored(pos), name, initials, color, temporary);
    }

    public static boolean addChunkWaypoint(String category, int chunkX, int chunkZ, int y, String name, String initials, MapUtil.WpColor color, boolean temporary, @Nullable String dimension) {
        BlockPos centre = new BlockPos(chunkX * 16 + 8, y, chunkZ * 16 + 8);
        return addWaypoint(category, centre, name, initials, color, temporary, dimension);
    }

    public static void removeWaypoint(String category, BlockPos pos) {
        if (!Utils.XAERO_AVAILABLE) return;
        forget(category, key -> key.x() == pos.getX() && key.y() == pos.getY() && key.z() == pos.getZ());
        MapUtil.removeWaypoints(category, pos::equals, Optional.empty());
    }

    public static void removeWaypointXZ(String category, int x, int z) {
        if (!Utils.XAERO_AVAILABLE) return;
        forget(category, key -> key.x() == x && key.z() == z);
        MapUtil.removeWaypoints(category, p -> p.getX() == x && p.getZ() == z, Optional.empty());
    }

    public static void clearCategory(String category) {
        if (!Utils.XAERO_AVAILABLE) return;
        TRACKED.remove(category);
        MapUtil.removeWaypoints(category, p -> true, Optional.empty());
    }

    public static void removeInRadius(String category, BlockPos center, double radius) {
        if (!Utils.XAERO_AVAILABLE) return;
        double limit = radius * radius;
        Predicate<BlockPos> inside = p -> {
            double dx = p.getX() - center.getX();
            double dz = p.getZ() - center.getZ();
            return dx * dx + dz * dz <= limit;
        };
        removeMatching(category, inside);
    }

    public static void removeMatching(String category, Predicate<BlockPos> predicate) {
        if (!Utils.XAERO_AVAILABLE) return;
        forget(category, key -> predicate.test(key.pos()));
        MapUtil.removeWaypoints(category, predicate, Optional.empty());
    }

    private static void forget(String category, Predicate<TrackedKey> matches) {
        Set<TrackedKey> keys = TRACKED.get(category);
        if (keys != null) {
            keys.removeIf(matches);
        }
    }

    public static boolean hasWaypoint(String category, BlockPos pos) {
        Set<TrackedKey> keys = TRACKED.get(category);
        return keys != null && keys.contains(new TrackedKey(pos.getX(), pos.getY(), pos.getZ(), category));
    }

    public static boolean hasWaypointXZ(String category, int x, int z) {
        Set<TrackedKey> keys = TRACKED.get(category);
        return keys != null && keys.stream().anyMatch(key -> key.x() == x && key.z() == z);
    }

    public static int getWaypointCount(String category) {
        Set<TrackedKey> keys = TRACKED.get(category);
        return keys == null ? 0 : keys.size();
    }

    public static Set<BlockPos> getTrackedPositions(String category) {
        Set<TrackedKey> keys = TRACKED.get(category);
        if (keys == null) return Set.of();
        Set<BlockPos> positions = new HashSet<>();
        for (TrackedKey key : keys) {
            positions.add(key.pos());
        }
        return positions;
    }

    public static void resetTracking() {
        TRACKED.clear();
    }

    public static void resetCategoryTracking(String category) {
        TRACKED.remove(category);
    }

    @Nullable
    public static String getCurrentDimension() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;
        return mc.world.getRegistryKey().getValue().getPath();
    }

    public static String formatEntityName(String entityType, int count) {
        return count > 1 ? count + "x " + entityType : entityType;
    }

    public enum DetectionType {
        STASH,
        TREASURE,
        MOB,
        ENTITY_STACK,
        PORTAL_ACTIVITY,
        STRUCTURE,
        PLAYER_BUILD,
        CUSTOM
    }

    public static String getDefaultEmoji(DetectionType type) {
        return switch (type) {
            case STASH -> "📦";
            case TREASURE -> "❌";
            case MOB -> "🐾";
            case ENTITY_STACK -> "⚠";
            case PORTAL_ACTIVITY -> "🌀";
            case STRUCTURE -> "🏛";
            case PLAYER_BUILD -> "🏠";
            case CUSTOM -> "📍";
        };
    }

    public static MapUtil.WpColor getDefaultColor(DetectionType type) {
        return switch (type) {
            case STASH, ENTITY_STACK -> MapUtil.WpColor.Gold;
            case TREASURE -> MapUtil.WpColor.Dark_Red;
            case MOB -> MapUtil.WpColor.Purple;
            case PORTAL_ACTIVITY -> MapUtil.WpColor.Dark_Purple;
            case STRUCTURE -> MapUtil.WpColor.Blue;
            case PLAYER_BUILD -> MapUtil.WpColor.Green;
            case CUSTOM -> MapUtil.WpColor.White;
        };
    }
}
