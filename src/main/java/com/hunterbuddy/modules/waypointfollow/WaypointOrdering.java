package com.hunterbuddy.modules.waypointfollow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.util.math.BlockPos;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

public final class WaypointOrdering {
    public static final int UNNUMBERED = Integer.MAX_VALUE;

    private WaypointOrdering() {
    }

    public static int orderNumber(String name, String prefix) {
        if (name == null || name.length() < prefix.length()) return UNNUMBERED;
        try {
            return Integer.parseInt(name.substring(prefix.length()));
        } catch (NumberFormatException e) {
            return UNNUMBERED;
        }
    }

    public static List<BlockPos> loadOrdered(MinimapWorld world, String prefix) {
        WaypointSet set = FollowerWaypointWorld.currentSet(world);
        if (set == null) return null;
        List<BlockPos> ordered = new ArrayList<>();
        for (Waypoint waypoint : enabled(set, prefix)) ordered.add(positionOf(waypoint));
        return ordered;
    }

    public static Set<BlockPos> enabledPositions(WaypointSet set, String prefix) {
        Set<BlockPos> positions = new HashSet<>();
        for (Waypoint waypoint : enabled(set, prefix)) positions.add(positionOf(waypoint));
        return positions;
    }

    public static int countFollowable(WaypointSet set, String prefix) {
        return followable(set, prefix).size();
    }

    public static int nextNumber(WaypointSet set, String prefix) {
        int highest = -1;
        for (Waypoint waypoint : followable(set, prefix)) {
            int number = orderNumber(waypoint.getName(), prefix);
            if (number != UNNUMBERED && number > highest) highest = number;
        }
        return highest + 1;
    }

    public static Waypoint findAt(WaypointSet set, String prefix, BlockPos pos) {
        for (Waypoint waypoint : set.getWaypoints()) {
            boolean named = waypoint.getName() != null && waypoint.getName().startsWith(prefix);
            if (named && pos.equals(positionOf(waypoint))) return waypoint;
        }
        return null;
    }

    private static BlockPos positionOf(Waypoint waypoint) {
        return new BlockPos(waypoint.getX(), waypoint.getY(), waypoint.getZ());
    }

    private static List<Waypoint> followable(WaypointSet set, String prefix) {
        return sortedMatching(set, prefix, waypoint -> true);
    }

    private static List<Waypoint> enabled(WaypointSet set, String prefix) {
        return sortedMatching(set, prefix, waypoint -> !waypoint.isDisabled());
    }

    private static List<Waypoint> sortedMatching(WaypointSet set, String prefix, Predicate<Waypoint> filter) {
        List<Waypoint> found = new ArrayList<>();
        for (Waypoint waypoint : set.getWaypoints()) {
            if (filter.test(waypoint) && BotWaypoints.shouldFollow(waypoint.getName(), prefix)) found.add(waypoint);
        }
        found.sort(Comparator.comparingInt(waypoint -> orderNumber(waypoint.getName(), prefix)));
        return found;
    }
}
