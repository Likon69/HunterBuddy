package com.hunterbuddy.modules.regear.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class XaeroWaypointManager {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Map<String, Set<WaypointKey>> categoryWaypoints = new HashMap<>();

    public static boolean isAvailable() {
        return Utils.XAERO_AVAILABLE;
    }

    // ==================== ADD WAYPOINT ====================

    public static boolean addWaypoint(String category, BlockPos pos, String name, String initials,
                                      MapUtil.WpColor color, boolean temporary, @Nullable String dimension) {
        if (!isAvailable()) return false;

        WaypointKey key = new WaypointKey(pos.getX(), pos.getY(), pos.getZ(), category);
        Set<WaypointKey> existing = categoryWaypoints.computeIfAbsent(category, k -> new HashSet<>());

        if (existing.contains(key)) return false;

        String fullName = category + " - " + name;
        MapUtil.addWaypoint(pos, fullName, initials, MapUtil.Purpose.Normal, color, temporary, dimension);
        existing.add(key);
        return true;
    }

    public static boolean addWaypoint(String category, BlockPos pos, String name, String initials,
                                      MapUtil.WpColor color, boolean temporary) {
        return addWaypoint(category, pos, name, initials, color, temporary, getCurrentDimension());
    }

    public static boolean addWaypoint(String category, Vec3d pos, String name, String initials,
                                      MapUtil.WpColor color, boolean temporary) {
        BlockPos blockPos = BlockPos.ofFloored(pos);
        return addWaypoint(category, blockPos, name, initials, color, temporary);
    }

    public static boolean addChunkWaypoint(String category, int chunkX, int chunkZ, int y,
                                           String name, String initials, MapUtil.WpColor color,
                                           boolean temporary, @Nullable String dimension) {
        int centerX = chunkX * 16 + 8;
        int centerZ = chunkZ * 16 + 8;
        return addWaypoint(category, new BlockPos(centerX, y, centerZ), name, initials, color, temporary, dimension);
    }

    // ==================== REMOVE WAYPOINT ====================

    public static void removeWaypoint(String category, BlockPos pos) {
        if (!isAvailable()) return;

        WaypointKey key = new WaypointKey(pos.getX(), pos.getY(), pos.getZ(), category);
        Set<WaypointKey> existing = categoryWaypoints.get(category);
        if (existing != null) {
            existing.remove(key);
        }

        MapUtil.removeWaypoints(category, p ->
            p.getX() == pos.getX() && p.getY() == pos.getY() && p.getZ() == pos.getZ(), Optional.empty());
    }

    public static void removeWaypointXZ(String category, int x, int z) {
        if (!isAvailable()) return;

        Set<WaypointKey> existing = categoryWaypoints.get(category);
        if (existing != null) {
            existing.removeIf(key -> key.x == x && key.z == z);
        }

        MapUtil.removeWaypoints(category, p -> p.getX() == x && p.getZ() == z, Optional.empty());
    }

    public static void clearCategory(String category) {
        if (!isAvailable()) return;
        categoryWaypoints.remove(category);
        MapUtil.removeWaypoints(category, p -> true, Optional.empty());
    }

    public static void removeInRadius(String category, BlockPos center, double radius) {
        if (!isAvailable()) return;

        double radiusSq = radius * radius;
        Set<WaypointKey> existing = categoryWaypoints.get(category);
        if (existing != null) {
            existing.removeIf(key -> {
                double dx = key.x - center.getX();
                double dz = key.z - center.getZ();
                return dx * dx + dz * dz <= radiusSq;
            });
        }

        MapUtil.removeWaypoints(category, p -> {
            double dx = p.getX() - center.getX();
            double dz = p.getZ() - center.getZ();
            return dx * dx + dz * dz <= radiusSq;
        }, Optional.empty());
    }

    public static void removeMatching(String category, Predicate<BlockPos> predicate) {
        if (!isAvailable()) return;

        Set<WaypointKey> existing = categoryWaypoints.get(category);
        if (existing != null) {
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            existing.removeIf(key -> {
                mutable.set(key.x, key.y, key.z);
                return predicate.test(mutable);
            });
        }

        MapUtil.removeWaypoints(category, predicate, Optional.empty());
    }

    // ==================== CHECKS ====================

    public static boolean hasWaypoint(String category, BlockPos pos) {
        Set<WaypointKey> existing = categoryWaypoints.get(category);
        return existing != null && existing.contains(new WaypointKey(pos.getX(), pos.getY(), pos.getZ(), category));
    }

    public static boolean hasWaypointXZ(String category, int x, int z) {
        Set<WaypointKey> existing = categoryWaypoints.get(category);
        return existing != null && existing.stream().anyMatch(key -> key.x == x && key.z == z);
    }

    public static int getWaypointCount(String category) {
        Set<WaypointKey> existing = categoryWaypoints.get(category);
        return existing == null ? 0 : existing.size();
    }

    public static Set<BlockPos> getTrackedPositions(String category) {
        Set<WaypointKey> existing = categoryWaypoints.get(category);
        if (existing == null || existing.isEmpty()) {
            return Set.of();
        }

        Set<BlockPos> positions = new HashSet<>();
        for (WaypointKey key : existing) {
            positions.add(new BlockPos(key.x, key.y, key.z));
        }

        return positions;
    }

    public static void resetTracking() {
        categoryWaypoints.clear();
    }

    public static void resetCategoryTracking(String category) {
        categoryWaypoints.remove(category);
    }

    @Nullable
    public static String getCurrentDimension() {
        return mc.world == null ? null : mc.world.getRegistryKey().getValue().getPath();
    }

    // ==================== HELPERS ====================

    public static String formatEntityName(String entityType, int count) {
        return count > 1 ? count + "x " + entityType : entityType;
    }

    public enum DetectionType {
        STASH, TREASURE, MOB, ENTITY_STACK, PORTAL_ACTIVITY, STRUCTURE, PLAYER_BUILD, CUSTOM
    }

    public static String getDefaultEmoji(DetectionType type) {
        return switch (type) {
            case STASH -> "📦";
            case TREASURE -> "❌";
            case MOB -> "🐾";
            case ENTITY_STACK -> "⚠";
            case PORTAL_ACTIVITY -> "🌀";
            case STRUCTURE -> "🏛️";
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

    // ==================== INTERNAL ====================

    private record WaypointKey(int x, int y, int z, String category) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WaypointKey that = (WaypointKey) o;
            return x == that.x && y == that.y && z == that.z && Objects.equals(category, that.category);
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z, category);
        }
    }
}
