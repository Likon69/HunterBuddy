package com.hunterbuddy.behavior.lab;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.farmer.FarmerCatalog;
import com.hunterbuddy.behavior.farmer.FarmerSettings;
import com.hunterbuddy.behavior.farmer.FarmerStepConfig;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.mc.BaritoneBridge;
import com.hunterbuddy.behavior.mc.BaseVisitContext;
import com.hunterbuddy.behavior.mc.WorldReader;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class BaseVisitLabContext {
    public final FarmerCatalog catalog;
    public final FarmerZoneLookup lookup;
    public final FarmerSettings settings;
    public final List<FarmerStepConfig> steps;
    public final CompletableFuture<Map<Key<?>, Object>> mission;
    public final String worldKey;
    public final WorldReader world;
    public final BaritoneBridge baritone;
    public final Outputs outputs;
    public BaseVisitContext visit;

    public BaseVisitLabContext(FarmerCatalog catalog, FarmerZoneLookup lookup, FarmerSettings settings,
                               CompletableFuture<Map<Key<?>, Object>> mission, String worldKey, WorldReader world,
                               BaritoneBridge baritone, Outputs outputs) {
        this.catalog = catalog;
        this.lookup = lookup;
        this.settings = settings;
        steps = settings.steps();
        this.mission = mission;
        this.worldKey = worldKey;
        this.world = world;
        this.baritone = baritone;
        this.outputs = outputs;
    }
}
