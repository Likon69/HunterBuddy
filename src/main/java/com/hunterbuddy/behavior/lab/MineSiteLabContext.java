package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.farmer.FarmerCatalog;
import com.hunterbuddy.behavior.farmer.FarmerStepConfig;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.MineSiteContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class MineSiteLabContext {
    public final FarmerCatalog catalog;
    public final FarmerZoneLookup lookup;
    public final List<FarmerStepConfig> steps;
    public final CompletableFuture<Map<Key<?>, Object>> mission;
    public final String worldKey;
    public final WorldReader world;
    public final BaritoneBridge baritone;
    public final Outputs outputs;
    public MineSiteContext mine;

    public MineSiteLabContext(FarmerCatalog catalog, FarmerZoneLookup lookup, List<FarmerStepConfig> steps,
                              CompletableFuture<Map<Key<?>, Object>> mission, String worldKey, WorldReader world,
                              BaritoneBridge baritone, Outputs outputs) {
        this.catalog = catalog;
        this.lookup = lookup;
        this.steps = List.copyOf(steps);
        this.mission = mission;
        this.worldKey = worldKey;
        this.world = world;
        this.baritone = baritone;
        this.outputs = outputs;
    }
}
