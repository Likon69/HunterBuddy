package com.hunterbuddy.modules.recast;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

public final class PitchSteering {
    private static final float DEAD_ZONE_DEGREES = 0.5F;
    private static final float PITCH_LIMIT = 89.0F;

    private PitchSteering() {
    }

    public static void steer(ClientPlayerEntity player, double targetPitch, double degreesPerTick) {
        float difference = (float) targetPitch - player.getPitch();
        if (Math.abs(difference) < DEAD_ZONE_DEGREES) return;
        float step = (float) Math.copySign(Math.min(Math.abs(difference), degreesPerTick), difference);
        player.setPitch(MathHelper.clamp(player.getPitch() + step, -PITCH_LIMIT, PITCH_LIMIT));
    }
}
