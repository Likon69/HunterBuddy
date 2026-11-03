package com.hunterbuddy.modules.regear.util;

import net.minecraft.client.MinecraftClient;

final class TakeoffBoostPhase {
    private static final int GLIDE_LOSS_TICKS = 6;
    private static final int FIRST_ROCKET_TIMEOUT = 20;
    private static final int CONFIRM_TICKS = 8;
    private static final int SECOND_ROCKET_TICK = 14;
    private static final int BOOST_TIMEOUT = 40;
    private static final double MIN_SPEED_BPS = 5.0;
    private static final double CLIMB_VELOCITY = 0.08;
    private static final double TICKS_PER_SECOND = 20.0;

    private TakeoffBoostPhase() {
    }

    static void post(MinecraftClient mc, TakeoffAttempt attempt, int rocketSlot, TakeoffControl control) {
        if (!mc.player.isGliding()) {
            if (attempt.phaseTicks > GLIDE_LOSS_TICKS) control.retry("lost glide before boost completed");
        } else if (!attempt.boosted) {
            if (attempt.phaseTicks >= 1) fireFirst(mc, attempt, rocketSlot, control);
        } else {
            confirm(mc, attempt, rocketSlot, control);
        }
    }

    private static void fireFirst(MinecraftClient mc, TakeoffAttempt attempt, int rocketSlot, TakeoffControl control) {
        if (TakeoffRocket.use(mc, rocketSlot)) {
            attempt.boosted = true;
            attempt.phaseTicks = 0;
            control.debug("Firework boost while gliding");
        } else if (attempt.phaseTicks > FIRST_ROCKET_TIMEOUT) {
            control.retry("no firework for boost");
        }
    }

    private static void confirm(MinecraftClient mc, TakeoffAttempt attempt, int rocketSlot, TakeoffControl control) {
        double speed = mc.player.getVelocity().length() * TICKS_PER_SECOND;
        boolean fast = speed >= MIN_SPEED_BPS || mc.player.getVelocity().y > CLIMB_VELOCITY;
        if (attempt.phaseTicks >= CONFIRM_TICKS && fast) {
            control.succeed(speed);
        } else if (attempt.phaseTicks == SECOND_ROCKET_TICK && speed < MIN_SPEED_BPS) {
            fireSecond(mc, attempt, rocketSlot, control);
        } else if (attempt.phaseTicks > BOOST_TIMEOUT) {
            control.retry("boost did not reach flight speed");
        }
    }

    private static void fireSecond(MinecraftClient mc, TakeoffAttempt attempt, int rocketSlot, TakeoffControl control) {
        if (!TakeoffRocket.use(mc, rocketSlot)) return;
        attempt.phaseTicks = 0;
        control.debug("Second firework boost");
    }
}
