package com.hunterbuddy.behavior.farmer;

import com.hunterbuddy.behavior.mc.XaeroWaypoints;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.util.math.BlockPos;

public final class FarmerZoneLookup {
    public static final double TARGET_MARGIN = 3;
    private final XaeroWaypoints waypoints;
    private final Supplier<String> zoneName;

    public FarmerZoneLookup(XaeroWaypoints waypoints, Supplier<String> zoneName) {
        this.waypoints = Objects.requireNonNull(waypoints);
        this.zoneName = Objects.requireNonNull(zoneName);
    }

    public String zoneName() {
        return zoneName.get();
    }

    public boolean waitingForWaypoints() {
        String name = zoneName();
        return name != null && !name.isBlank() && waypoints.named(name).size() < 4;
    }

    public Resolved resolve(String hint) {
        String name = zoneName();
        if (name == null || name.isBlank()) return Resolved.refused("work zone name is empty");
        List<BlockPos> corners = waypoints.named(name);
        if (corners.size() != 4) {
            return Resolved.refused("waypoints '" + name + "' found=" + corners.size() + " (4 required)");
        }
        FarmerZone zone;
        try {
            zone = FarmerZone.build(corners);
        } catch (IllegalArgumentException ex) {
            return Resolved.refused("work zone '" + name + "': " + ex.getMessage());
        }
        BlockPos site = hint == null || hint.isBlank() ? null : waypoints.named(hint).stream()
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
