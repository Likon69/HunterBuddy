package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.regear.arealoader.AreaMath;

final class SpiralSaveCheck {
    private static final double SUSPICIOUS_DISTANCE = 1000.0;

    private SpiralSaveCheck() {
    }

    static boolean isCorrupt(Spiral.PathingDataSpiral data) {
        if (data.currPos == null) return true;
        if (data.spiralOrigin == null && data.initialPos == null) return true;
        return hasNoGrowthYetIsFarFromOrigin(data);
    }

    private static boolean hasNoGrowthYetIsFarFromOrigin(Spiral.PathingDataSpiral data) {
        if (data.spiralOrigin == null) return false;
        boolean noGrowth = data.spiralWidth == 0 && data.spiralHeight == 0;
        return noGrowth && AreaMath.horizontalDistance(data.currPos, data.spiralOrigin) > SUSPICIOUS_DISTANCE;
    }

    static void fillMissingCorner(Spiral.PathingDataSpiral data) {
        if (data.spiralOrigin == null) {
            data.spiralOrigin = data.initialPos;
        }
        if (data.initialPos == null) {
            data.initialPos = data.spiralOrigin;
        }
    }
}
