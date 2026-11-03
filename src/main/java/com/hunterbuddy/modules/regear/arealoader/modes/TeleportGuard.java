package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.regear.arealoader.AreaMath;
import net.minecraft.util.math.BlockPos;

final class TeleportGuard {
    private static final double TELEPORT_DISTANCE = 100.0;

    private BlockPos lastTickPosition;
    private boolean paused;

    boolean isPaused() {
        return paused;
    }

    BlockPos lastTickPosition() {
        return lastTickPosition;
    }

    boolean hasTeleported(BlockPos now) {
        return !paused && lastTickPosition != null && AreaMath.horizontalDistance(lastTickPosition, now) > TELEPORT_DISTANCE;
    }

    void pause() {
        paused = true;
    }

    void remember(BlockPos now) {
        lastTickPosition = now;
    }

    void reset() {
        lastTickPosition = null;
        paused = false;
    }
}
