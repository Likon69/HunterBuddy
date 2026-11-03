package com.hunterbuddy.modules.regear.util;

import net.minecraft.client.MinecraftClient;

final class TakeoffJumpPhase {
    private static final int JUMP_PULSE_BUDGET = 6;
    private static final int JUMP_TIMEOUT = 40;
    private static final int MIN_AIRBORNE = 3;

    private TakeoffJumpPhase() {
    }

    static void pre(MinecraftClient mc, TakeoffAttempt attempt, TakeoffControl control) {
        boolean pulse = mc.player.age % 2 == 0;
        if (!mc.player.isOnGround()) {
            setJump(mc, pulse);
        } else if (!attempt.jumped) {
            firstJump(mc, attempt, control);
        } else {
            setJump(mc, attempt.jumpHoldTicks > 0 && pulse);
        }
    }

    static void post(MinecraftClient mc, TakeoffAttempt attempt, TakeoffControl control) {
        if (mc.player.isGliding()) {
            control.enterBoosting();
        } else if (!mc.player.isOnGround()) {
            countAirborne(attempt, control);
        } else {
            attempt.airborneTicks = 0;
            if (attempt.jumped && attempt.phaseTicks > JUMP_TIMEOUT) control.retry("jump did not leave ground");
        }
    }

    private static void firstJump(MinecraftClient mc, TakeoffAttempt attempt, TakeoffControl control) {
        Utils.holdJump(mc);
        attempt.jumped = true;
        attempt.jumpHoldTicks = JUMP_PULSE_BUDGET;
        control.debug("Takeoff jump (baritone input)");
    }

    private static void countAirborne(TakeoffAttempt attempt, TakeoffControl control) {
        attempt.airborneTicks++;
        if (attempt.airborneTicks < MIN_AIRBORNE) return;
        control.toDeploying();
    }

    private static void setJump(MinecraftClient mc, boolean pressed) {
        if (pressed) {
            Utils.holdJump(mc);
        } else {
            Utils.releaseJump(mc);
        }
    }
}
