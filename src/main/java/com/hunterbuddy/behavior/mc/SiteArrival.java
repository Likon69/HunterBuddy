package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.behavior.farmer.MaterialSite;
import net.minecraft.util.math.BlockPos;

public final class SiteArrival {
    private SiteArrival() {}

    public static BlockPos stand(MaterialSite site, FarmerProfile profile, WorldReader world) {
        return profile.strategy().equals("layer") ? world.surface(site.x, site.z) : site.seed().up();
    }

    public static double distanceSquared(MaterialSite site, FarmerProfile profile, WorldReader world) {
        if (!profile.strategy().equals("layer")) return world.feet().getSquaredDistance(site.seed());
        double x = world.feet().getX() - site.x;
        double z = world.feet().getZ() - site.z;
        return x * x + z * z;
    }
}
