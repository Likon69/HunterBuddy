package com.hunterbuddy.behavior.farmer;

import com.hunterbuddy.behavior.mc.XaeroWaypoints;
import java.util.List;
import java.util.Objects;
import net.minecraft.util.math.BlockPos;

public final class FarmerZoneLookup {
    public static final double TARGET_MARGIN = 3;
    private final XaeroWaypoints waypoints;

    public FarmerZoneLookup(XaeroWaypoints waypoints) {
        this.waypoints = Objects.requireNonNull(waypoints);
    }

    public Resolved resolve(String stepName) {
        if (stepName == null || stepName.isBlank()) return Resolved.refused("step has no zone name");
        List<BlockPos> corners = waypoints.named(stepName + " zone");
        if (corners.size() != 4) {
            return Resolved.refused("waypoints '" + stepName + " zone' found=" + corners.size() + " (4 required)");
        }
        FarmerZone zone;
        try {
            zone = FarmerZone.build(corners);
        } catch (IllegalArgumentException ex) {
            return Resolved.refused(ex.getMessage());
        }
        BlockPos site = waypoints.named(stepName).stream()
            .filter(pos -> zone.contains(pos.getX() + 0.5, pos.getZ() + 0.5))
            .findFirst().orElse(null);
        return Resolved.accepted(zone, site);
    }

    public record Resolved(FarmerZone zone, BlockPos siteHint, String refusal) {
        private static Resolved refused(String reason) {
            return new Resolved(null, null, reason);
        }

        private static Resolved accepted(FarmerZone zone, BlockPos site) {
            return new Resolved(zone, site, null);
        }

        public boolean ok() {
            return refusal == null;
        }
    }
}
