package com.hunterbuddy.modules.mapwaypoint;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.world.MapWorld;

public enum MapDimension {
    OVERWORLD,
    NETHER,
    END;

    private static final String NETHER_PATH = "the_nether";
    private static final String END_PATH = "the_end";

    public static MapDimension viewed() {
        try {
            return fromPath(viewedKey().getValue().getPath());
        } catch (Exception failure) {
            return OVERWORLD;
        }
    }

    private static RegistryKey<World> viewedKey() {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        MapProcessor processor = session.getMapProcessor();
        MapWorld mapWorld = processor.getMapWorld();
        return mapWorld.getCurrentDimensionId();
    }

    private static MapDimension fromPath(String path) {
        if (NETHER_PATH.equals(path)) return NETHER;
        if (END_PATH.equals(path)) return END;
        return OVERWORLD;
    }
}
