package com.hunterbuddy.modules.flowesp;

import net.minecraft.fluid.FluidState;

final class NetherRules implements ScanRules {
    private static final int SCAN_TOP = 128;

    private final DimensionOptions options;

    NetherRules(DimensionOptions options) {
        this.options = options;
    }

    @Override
    public int minY() {
        return options.netherMinY.get();
    }

    @Override
    public int maxY() {
        return SCAN_TOP;
    }

    @Override
    public boolean counts(FluidState fluid) {
        return ScanRules.isLava(fluid);
    }

    @Override
    public boolean isRoof(int y, FluidState fluid) {
        return ScanRules.isLava(fluid) && y >= options.netherRoofY.get();
    }

    @Override
    public boolean filtersLakes() {
        return false;
    }
}
