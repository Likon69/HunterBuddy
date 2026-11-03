package com.hunterbuddy.modules.waypointfollow;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class YawSteering {
    private static final float YAW_OFFSET = 90.0F;

    private YawSteering() {
    }

    public static void snap(Vec3d target, double deadzone) {
        steer(target, deadzone, (current, targetYaw, error) -> targetYaw);
    }

    public static void smooth(Vec3d target, double deadzone, double speed) {
        steer(target, deadzone, (current, targetYaw, error) -> (float) (current + error * speed));
    }

    private static void steer(Vec3d target, double deadzone, Turn turn) {
        ClientPlayerEntity player = MeteorClient.mc.player;
        if (player == null) return;
        float targetYaw = yawToward(player.getEntityPos(), target);
        float error = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        if (Math.abs(error) > deadzone) player.setYaw(turn.apply(player.getYaw(), targetYaw, error));
    }

    private static float yawToward(Vec3d from, Vec3d to) {
        return (float) Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - YAW_OFFSET;
    }

    private interface Turn {
        float apply(float current, float targetYaw, float error);
    }
}
