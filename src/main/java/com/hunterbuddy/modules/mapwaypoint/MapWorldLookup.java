package com.hunterbuddy.modules.mapwaypoint;

import com.hunterbuddy.modules.WaypointFollower;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

final class MapWorldLookup {
    private static final String OVERWORLD_PATH = "overworld";

    private MapWorldLookup() {
    }

    static MinimapWorld targetFor(MinimapSession session, MapDimension viewed) {
        if (viewed == MapDimension.END) return session.getWorldManager().getCurrentWorld();
        MinimapWorld resolved = WaypointFollower.resolveOverworldWaypointWorld();
        return resolved != null ? resolved : scanForOverworld(session);
    }

    private static MinimapWorld scanForOverworld(MinimapSession session) {
        try {
            MinimapWorldRootContainer root = session.getWorldManager().getCurrentRootContainer();
            for (MinimapWorld world : root.getWorlds()) {
                if (OVERWORLD_PATH.equals(world.getDimId().getValue().getPath())) return world;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }
}
