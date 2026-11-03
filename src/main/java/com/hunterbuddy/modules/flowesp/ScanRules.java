package com.hunterbuddy.modules.flowesp;

import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;

interface ScanRules {
    int minY();

    int maxY();

    boolean counts(FluidState fluid);

    boolean isRoof(int y, FluidState fluid);

    boolean filtersLakes();

    static boolean isLava(FluidState fluid) {
        return fluid.isIn(FluidTags.LAVA);
    }

    static boolean isWater(FluidState fluid) {
        return fluid.isIn(FluidTags.WATER);
    }
}
