package com.hunterbuddy.modules.regear.util;

import net.minecraft.client.MinecraftClient;

final class TakeoffDeployPhase {
    private static final int DEPLOY_TIMEOUT = 40;
    private static final int GROUND_GRACE_TICKS = 4;
    private static final int GROUND_TICKS_TO_FAIL = 2;
    private static final int AIR_WAIT = 2;
    private static final int RETRY_INTERVAL = 4;
    private static final int MAX_DEPLOY_ATTEMPTS = 4;

    private TakeoffDeployPhase() {
    }

    static void pre(MinecraftClient mc, TakeoffAttempt attempt) {
        if (attempt.jumpTapTicks > 0) {
            attempt.jumpTapTicks--;
            Utils.holdJump(mc);
        } else {
            Utils.releaseJump(mc);
        }
    }

    static void post(MinecraftClient mc, TakeoffAttempt attempt, TakeoffControl control) {
        if (mc.player.isGliding()) {
            control.enterBoosting();
            return;
        }
        if (mc.player.isOnGround()) {
            if (hasLanded(attempt)) {
                control.retry("landed before elytra deployed");
                return;
            }
        } else {
            countAirborne(attempt, control);
        }
        if (attempt.phaseTicks > DEPLOY_TIMEOUT) control.retry("elytra deploy timed out");
    }

    private static boolean hasLanded(TakeoffAttempt attempt) {
        attempt.groundTicks++;
        return attempt.phaseTicks >= GROUND_GRACE_TICKS && attempt.groundTicks >= GROUND_TICKS_TO_FAIL;
    }

    private static void countAirborne(TakeoffAttempt attempt, TakeoffControl control) {
        attempt.groundTicks = 0;
        attempt.airborneTicks++;
        if (!isTapDue(attempt)) return;
        attempt.jumpTapTicks = 1;
        attempt.deployAttempts++;
        control.debug("Deploy attempt " + attempt.deployAttempts);
    }

    private static boolean isTapDue(TakeoffAttempt attempt) {
        boolean waited = attempt.airborneTicks >= AIR_WAIT;
        boolean spaced = attempt.deployAttempts == 0 || attempt.airborneTicks % RETRY_INTERVAL == 0;
        return waited && spaced && attempt.deployAttempts < MAX_DEPLOY_ATTEMPTS;
    }
}
