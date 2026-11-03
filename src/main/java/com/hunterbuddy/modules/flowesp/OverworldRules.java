package com.hunterbuddy.modules.flowesp;

import net.minecraft.fluid.FluidState;

final class OverworldRules implements ScanRules {
    private final DimensionOptions options;

    OverworldRules(DimensionOptions options) {
        this.options = options;
    }

    @Override
    public int minY() {
        return options.overworldMinY.get();
    }

    @Override
    public int maxY() {
        return options.overworldMaxY.get();
    }

    @Override
    public boolean counts(FluidState fluid) {
        return ScanRules.isWater(fluid) || (options.detectLava.get() && ScanRules.isLava(fluid));
    }

    @Override
    public boolean isRoof(int y, FluidState fluid) {
        return false;
    }

    @Override
    public boolean filtersLakes() {
        return true;
    }
}
