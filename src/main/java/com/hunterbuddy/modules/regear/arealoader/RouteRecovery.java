package com.hunterbuddy.modules.regear.arealoader;

import net.minecraft.util.math.BlockPos;

final class RouteRecovery {
    private static final int OFF_TRACK_CHECKS = 10;
    private static final double MAX_OFF_TRACK_BLOCKS = 200.0;
    private static final long COOLDOWN_MS = 5000;
    private static final int FAR_REACH_FACTOR = 2;

    private BlockPos target;
    private BlockPos lastGood;
    private int offTrackChecks;
    private long finishedAtMs;

    boolean isActive() {
        return target != null;
    }

    BlockPos target() {
        return target;
    }

    BlockPos lastGood() {
        return lastGood;
    }

    boolean isCoolingDown() {
        return System.currentTimeMillis() - finishedAtMs < COOLDOWN_MS;
    }

    void recordGoodPosition(BlockPos position) {
        lastGood = position;
    }

    boolean registerOffTrack(double distance, BlockPos playerPosition) {
        if (distance <= MAX_OFF_TRACK_BLOCKS) {
            offTrackChecks = 0;
            lastGood = playerPosition;
            return false;
        }
        offTrackChecks++;
        return offTrackChecks >= OFF_TRACK_CHECKS;
    }

    boolean isLastGoodWorthReturningTo(BlockPos playerPosition, int reach) {
        return lastGood != null && AreaMath.horizontalDistance(lastGood, playerPosition) > FAR_REACH_FACTOR * reach;
    }

    void begin(BlockPos recoveryTarget) {
        target = recoveryTarget;
        offTrackChecks = 0;
    }

    void finish(BlockPos playerPosition) {
        target = null;
        offTrackChecks = 0;
        finishedAtMs = System.currentTimeMillis();
        lastGood = playerPosition;
    }

    void reset() {
        target = null;
        lastGood = null;
        offTrackChecks = 0;
        finishedAtMs = 0;
    }
}
