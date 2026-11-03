package com.hunterbuddy.modules.waypointfollow;

import com.hunterbuddy.modules.regear.util.XaeroWaypointManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;

public final class BotWaypoints {
    private static final List<String> BOT_PREFIXES = List.of("AreaLoader", "StashFinder", "VanityESP", "TrailFollower");
    private static final String SEPARATOR_SPACED = " - ";
    private static final String SEPARATOR_DASH = "-";

    private BotWaypoints() {
    }

    public static boolean matchesPrefix(String name, String prefix) {
        return name != null && (name.startsWith(prefix)
            || name.startsWith(prefix + SEPARATOR_SPACED)
            || name.startsWith(prefix + SEPARATOR_DASH));
    }

    public static boolean isBotManaged(String name) {
        return name != null && BOT_PREFIXES.stream().anyMatch(prefix -> matchesPrefix(name, prefix));
    }

    public static boolean shouldFollow(String name, String huntPrefix) {
        return name != null && !isBotManaged(name) && name.startsWith(huntPrefix);
    }

    public static Set<BlockPos> positionsFor(MinimapWorld world, String prefix) {
        Set<BlockPos> positions = new HashSet<>(XaeroWaypointManager.getTrackedPositions(prefix));
        try {
            WaypointSet set = FollowerWaypointWorld.currentSet(world);
            if (set != null) addFromSet(set, prefix, positions);
        } catch (Exception ignored) {
        }
        return positions;
    }

    public static Set<BlockPos> positionsForAll(MinimapWorld world) {
        Set<BlockPos> positions = new HashSet<>();
        for (String prefix : BOT_PREFIXES) positions.addAll(positionsFor(world, prefix));
        return positions;
    }

    private static void addFromSet(WaypointSet set, String prefix, Set<BlockPos> into) {
        for (Waypoint waypoint : set.getWaypoints()) {
            if (matchesPrefix(waypoint.getName(), prefix)) {
                into.add(new BlockPos(waypoint.getX(), waypoint.getY(), waypoint.getZ()));
            }
        }
    }
}
