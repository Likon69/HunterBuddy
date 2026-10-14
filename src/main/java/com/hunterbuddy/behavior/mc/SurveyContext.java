package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.core.Key;
import com.hunterbuddy.behavior.core.Scope;
import com.hunterbuddy.behavior.farmer.FarmerCatalog;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerStepConfig;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import com.hunterbuddy.behavior.farmer.WorldKnowledge;
import com.hunterbuddy.behavior.farmer.ZoneKnowledge;
import com.hunterbuddy.behavior.resource.Outputs;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.util.math.BlockPos;

public final class SurveyContext {
    public final WorldReader world;
    public final BaritoneBridge baritone;
    public final Outputs outputs;
    public final FarmerCatalog catalog;
    public final FarmerZoneLookup lookup;
    public final List<FarmerStepConfig> steps;
    public final CompletableFuture<Map<Key<?>, Object>> mission;
    public final BlockPos home;
    public final String worldKey;
    public final Key<BlockPos> verifiedSite = new Key<>("survey-verified-site", BlockPos.class, Scope.OWNER, false);
    public FarmerStepConfig step;
    public FarmerProfile profile;
    public FarmerZone zone;
    public WorldKnowledge knowledge;
    public ZoneKnowledge memory;
    public ZoneCoverage coverage;
    public SurveyScanner scanner;
    public SurveyRoute route;
    public MaterialSite selected;
    public BlockPos arrivalStand;
    public String result = "prospection incomplète";
    public boolean missionMode;
    public boolean interrupted;
    public BooleanSupplier returnRequested = () -> false;
    public Predicate<MaterialSite> eligible = site -> true;

    public SurveyContext(WorldReader world, BaritoneBridge baritone, Outputs outputs, FarmerCatalog catalog,
                         FarmerZoneLookup lookup, List<FarmerStepConfig> steps,
                         CompletableFuture<Map<Key<?>, Object>> mission, BlockPos home, String worldKey) {
        this.world = world;
        this.baritone = baritone;
        this.outputs = outputs;
        this.catalog = catalog;
        this.lookup = lookup;
        this.steps = List.copyOf(steps);
        this.mission = mission;
        this.home = home;
        this.worldKey = worldKey;
    }

    public MaterialSite best() {
        return SiteSelection.best(memory, zone, world.feet(), System.currentTimeMillis(), eligible);
    }

    public boolean temporarilyUnavailable() {
        return memory != null && memory.sites.values().stream().anyMatch(site -> site.usable()
            && zone.containsWithMargin(site.x + 0.5, site.z + 0.5, FarmerZoneLookup.TARGET_MARGIN)
            && (site.flightAvoided() || !eligible.test(site)));
    }

    public String coverageReport() {
        return coverage.verified() + "/" + coverage.chunks().size() + " chunks vérifiés";
    }
}
