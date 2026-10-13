package com.hunterbuddy.behavior.mc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaeroplus.feature.waypoint.WaypointAPI;

public final class XaeroWaypoints {
    private final WorldReader world;

    public XaeroWaypoints(WorldReader world) {
        this.world = Objects.requireNonNull(world);
    }

    public List<BlockPos> named(String exactName) {
        Objects.requireNonNull(exactName);
        List<BlockPos> result = new ArrayList<>();
        try {
            WaypointSet set = currentSet();
            if (set == null) return List.of();
            for (Waypoint waypoint : set.getWaypoints()) {
                if (!waypoint.isDisabled() && exactName.equals(waypoint.getName())) {
                    result.add(new BlockPos(waypoint.getX(), waypoint.getY(), waypoint.getZ()));
                }
            }
        } catch (RuntimeException ignored) {
        }
        return List.copyOf(result);
    }

    private WaypointSet currentSet() {
        MinimapWorld waypointWorld = resolveWorld();
        return waypointWorld == null ? null : waypointWorld.getCurrentWaypointSet();
    }

    private MinimapWorld resolveWorld() {
        MinecraftClient client = world.client();
        if (client.world == null) return null;
        try {
            MinimapSession session = (MinimapSession) BuiltInHudModules.MINIMAP.getCurrentSession();
            if (session == null) return WaypointAPI.getMinimapWorld(client.world.getRegistryKey());
            MinimapWorld current = session.getWorldManager().getCurrentWorld();
            return current != null ? current : WaypointAPI.getMinimapWorld(client.world.getRegistryKey());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
