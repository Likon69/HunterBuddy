package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import com.hunterbuddy.behavior.farmer.ZoneKnowledge;
import java.util.Comparator;
import java.util.function.Predicate;
import net.minecraft.util.math.BlockPos;

public final class SiteSelection {
    private SiteSelection() {}

    public static MaterialSite best(ZoneKnowledge memory, FarmerZone zone, BlockPos from, long now) {
        return best(memory, zone, from, now, false, site -> true);
    }

    public static MaterialSite best(ZoneKnowledge memory, FarmerZone zone, BlockPos from, long now,
                                    Predicate<MaterialSite> eligible) {
        return best(memory, zone, from, now, false, eligible);
    }

    public static MaterialSite bestVerified(ZoneKnowledge memory, FarmerZone zone, BlockPos from, long now) {
        return best(memory, zone, from, now, true, site -> true);
    }

    private static MaterialSite best(ZoneKnowledge memory, FarmerZone zone, BlockPos from, long now, boolean verified,
                                     Predicate<MaterialSite> eligible) {
        return memory.sites.values().stream().filter(MaterialSite::usable)
            .filter(eligible)
            .filter(site -> !verified || site.status.equals("verified") && site.verifiedAt > 0)
            .filter(site -> zone.containsWithMargin(site.x + 0.5, site.z + 0.5, FarmerZoneLookup.TARGET_MARGIN))
            .max(Comparator.comparingDouble(site -> score(site, from, now))).orElse(null);
    }

    public static double score(MaterialSite site, BlockPos from, long now) {
        double distance = Math.sqrt(site.seed().getSquaredDistance(from));
        double freshness = 1 / (1 + Math.max(0, now - site.observedAt) / 86_400_000.0);
        double access = Math.max(1, Math.min(distance / 4.3, 40 + distance / 20));
        return site.quantity * freshness / access;
    }
}
