package com.hunterbuddy.behavior.farmer;

import com.hunterbuddy.behavior.mc.WorldReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;

public final class FarmerZoneReport {
    private static final int SCAN_RADIUS = 16;

    private FarmerZoneReport() {
    }

    public static List<String> lines(FarmerStepConfig step, FarmerZoneLookup lookup, FarmerCatalog catalog, WorldReader world) {
        List<String> lines = new ArrayList<>();
        if (step == null) {
            lines.add("FarmerBuddy: no enabled step configured");
            return lines;
        }
        lines.add("Step " + step.index() + " '" + step.name() + "' profile=" + step.profile());
        FarmerZoneLookup.Resolved resolved = lookup.resolve(step.name());
        if (!resolved.ok()) {
            lines.add("Zone refused: " + resolved.refusal());
            return lines;
        }
        FarmerZone zone = resolved.zone();
        lines.add("Corners: " + zone.corners().stream().map(BlockPos::toShortString).toList());
        lines.add(String.format(Locale.ROOT, "Area: %.0f blocks^2", zone.area()));
        lines.add("Site hint: " + (resolved.siteHint() == null ? "none" : resolved.siteHint().toShortString()));
        Optional<FarmerProfile> profile = catalog.profile(step.profile());
        if (profile.isEmpty()) {
            lines.add("Unknown catalog profile: " + step.profile());
            return lines;
        }
        if (!world.available()) {
            lines.add("Minecraft world is unavailable; block count skipped");
            return lines;
        }
        long accepted = 0;
        long refused = 0;
        BlockPos center = world.feet();
        int bottom = world.bottomY();
        int top = world.topY();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                BlockPos column = new BlockPos(x, center.getY(), z);
                if (!world.loaded(column)) continue;
                boolean inside = zone.contains(x + 0.5, z + 0.5);
                for (int y = bottom; y <= top; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!profile.get().blocks().contains(world.state(pos).getBlock())) continue;
                    if (inside) accepted++; else refused++;
                }
            }
        }
        lines.add("Blocks accepted in zone: " + accepted + " | refused out of zone: " + refused
            + " (scan radius=" + SCAN_RADIUS + " around " + center.toShortString() + ")");
        return lines;
    }
}
