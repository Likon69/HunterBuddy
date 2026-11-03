package com.hunterbuddy.modules.regear.util;

final class TakeoffAttempt {
    int phaseTicks;
    int airborneTicks;
    int deployAttempts;
    int jumpHoldTicks;
    int groundTicks;
    int jumpTapTicks;
    boolean jumped;
    boolean boosted;

    void reset() {
        phaseTicks = 0;
        airborneTicks = 0;
        deployAttempts = 0;
        jumpHoldTicks = 0;
        groundTicks = 0;
        jumpTapTicks = 0;
        jumped = false;
        boosted = false;
    }

    void resetForDeploy() {
        phaseTicks = 0;
        airborneTicks = 0;
        deployAttempts = 0;
        groundTicks = 0;
    }

    void resetForBoost() {
        phaseTicks = 0;
        boosted = false;
    }
}
