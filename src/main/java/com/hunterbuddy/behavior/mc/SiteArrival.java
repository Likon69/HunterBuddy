package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.FarmerZone;
import com.hunterbuddy.behavior.farmer.FarmerZoneLookup;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

public final class SiteArrival {
    private SiteArrival() {}

    public static BlockPos stand(MaterialSite site, FarmerProfile profile, FarmerZone zone, WorldReader world) {
        boolean layer = profile.strategy().equals("layer");
        BlockPos origin = site.seed().up();
        BlockPos best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (int radius = 0; radius <= 16; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    if (!zone.containsWithMargin(x + 0.5, z + 0.5, FarmerZoneLookup.TARGET_MARGIN)
                        || !world.loaded(new BlockPos(x, origin.getY(), z))) continue;
                    int surface = world.client().world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                    int first = layer ? surface : Math.min(world.topY() - 1, origin.getY() + 16);
                    int last = layer ? surface : Math.max(world.bottomY() + 1, origin.getY() - 16);
                    for (int y = first; y >= last; y--) {
                        BlockPos point = new BlockPos(x, y, z);
                        if (!practical(point, world)) continue;
                        double candidate = layer ? dx * dx + dz * dz : origin.getSquaredDistance(point);
                        if (candidate < distance) {
                            best = point;
                            distance = candidate;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static boolean practical(BlockPos point, WorldReader world) {
        return world.inBounds(point.down()) && world.inBounds(point.up()) && world.loaded(point)
            && world.state(point).isAir() && world.state(point.up()).isAir()
            && world.state(point.down()).getFluidState().isEmpty()
            && world.state(point.down()).isSolidBlock(world.client().world, point.down());
    }

}
