package com.hunterbuddy.modules.scaffold;

import com.hunterbuddy.modules.regear.util.RotationUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

final class ScaffoldRotation {
    private Runnable action;
    private Vec3d point;
    private boolean rotate;
    private boolean restore;
    private ClientPlayerEntity player;
    private float yaw;
    private float pitch;
    private float clickYaw;
    private float clickPitch;
    private boolean aligned;
    private float serverYaw = Float.NaN;
    private float serverPitch = Float.NaN;

    boolean schedule(Vec3d point, boolean rotate, boolean restore, Runnable action) {
        if (this.action != null) return false;
        this.point = point;
        this.rotate = rotate;
        this.restore = restore;
        this.action = action;
        return true;
    }

    void pre() {
        if (action == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.hasVehicle() || mc.getCameraEntity() != mc.player) {
            reset();
            return;
        }
        player = mc.player;
        yaw = player.getYaw();
        pitch = player.getPitch();
        clickYaw = yaw;
        clickPitch = pitch;
        aligned = false;
        if (rotate) {
            float[] angles = RotationUtils.getRotationsTo(player.getEyePos(), point);
            clickYaw = angles[0];
            clickPitch = angles[1];
            player.setYaw(clickYaw);
            player.setPitch(clickPitch);
        }
    }

    void sent(PlayerMoveC2SPacket packet) {
        if (packet.changesLook()) {
            serverYaw = packet.getYaw(0);
            serverPitch = packet.getPitch(0);
        }
        if (player == null) return;
        aligned = !rotate || Math.abs(MathHelper.wrapDegrees(serverYaw - clickYaw)) < 0.001f
            && Math.abs(serverPitch - clickPitch) < 0.001f;
    }

    void post() {
        Runnable placement = action;
        action = null;
        try {
            if (aligned && placement != null) placement.run();
        } finally {
            reset();
        }
    }

    void reset() {
        if (player != null && rotate && restore) {
            player.setYaw(yaw);
            player.setPitch(pitch);
        }
        player = null;
        action = null;
        point = null;
        aligned = false;
    }
}
