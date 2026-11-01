package com.hunterbuddy.modules;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.render.DroneEasing;
import com.hunterbuddy.render.DronePlacement;
import com.hunterbuddy.render.DronePose;
import com.hunterbuddy.render.DroneRig;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class DroneCamera extends Module {
    private static final double RETURN_DELAY_SECONDS = 3.0;
    private static final double RAMP_IN_SECONDS = 1.5;
    private static final double RAMP_OUT_SECONDS = 2.5;
    private static final double MAX_FRAME_SECONDS = 0.1;
    private static final double MIN_LOOK_DISTANCE = 0.05;
    private static final double FIRST_PERSON_DISTANCE = 1.0;
    private static final double ROTATION_TAU = 0.25;
    private static final double VANILLA_ROTATION_TAU = 0.25;
    private static final float MAX_PITCH = 89.0f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> shotSeconds = sgGeneral.add(new DoubleSetting.Builder()
        .name("shot-seconds")
        .description("How long each shot lasts before the camera blends into the next one. The fly-by ends earlier once you are well past it.")
        .defaultValue(20.0).min(3.0).max(120.0).sliderRange(3.0, 60.0)
        .build());

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("How far the camera sits from you, in blocks. It is pulled in whenever terrain is in the way.")
        .defaultValue(8.0).min(3.0).max(30.0).sliderRange(3.0, 30.0)
        .build());

    private final DroneRig rig = new DroneRig();

    private double activation;
    private long lastFrameNanos;
    private long lastGlideNanos;
    private boolean filming;
    private boolean rotationReady;
    private float yaw;
    private float pitch;
    private float vanillaYaw;
    private float vanillaPitch;

    public DroneCamera() {
        super(HunterBuddyAddon.VISUALS_CATEGORY, "drone-camera",
            "Films you from outside while you glide with an elytra, changing shot by itself. Only changes what is drawn.");
    }

    @Override
    public void onDeactivate() {
        halt();
    }

    public boolean isFilming() {
        return filming;
    }

    public DronePose pose(Vec3d eyePos, float eyeYaw, float eyePitch, Entity focused, float tickProgress) {
        ClientPlayerEntity player = mc.player;
        if (!isActive() || player == null || mc.world == null || focused != player || suspended()) {
            halt();
            return null;
        }

        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.0 : Math.min((now - lastFrameNanos) / 1.0e9, MAX_FRAME_SECONDS);
        lastFrameNanos = now;

        if (player.isGliding()) lastGlideNanos = now;
        boolean wanted = lastGlideNanos != 0L && (now - lastGlideNanos) / 1.0e9 < RETURN_DELAY_SECONDS;

        if (wanted) {
            activation = Math.min(1.0, activation + dt / RAMP_IN_SECONDS);
        } else {
            activation = Math.max(0.0, activation - dt / RAMP_OUT_SECONDS);
        }

        if (!wanted && activation <= 0.0) {
            halt();
            return null;
        }

        Vec3d focus = player.getLerpedPos(tickProgress).add(0.0, player.getHeight() * 0.5, 0.0);
        Vec3d motion = new Vec3d(player.getX() - player.lastX, player.getY() - player.lastY, player.getZ() - player.lastZ);
        DronePlacement placement = rig.update(mc.world, player, focus, motion, dt, distance.get(), shotSeconds.get());

        Vec3d look = placement.lookTarget().subtract(placement.position());
        if (look.length() < MIN_LOOK_DISTANCE) look = rig.flightDirection();

        float targetYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float targetPitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z))), -MAX_PITCH, MAX_PITCH);

        if (rotationReady) {
            float follow = (float) DroneEasing.follow(dt, ROTATION_TAU);
            float vanillaFollow = (float) DroneEasing.follow(dt, VANILLA_ROTATION_TAU);
            yaw += MathHelper.wrapDegrees(targetYaw - yaw) * follow;
            pitch += (targetPitch - pitch) * follow;
            vanillaYaw += MathHelper.wrapDegrees(eyeYaw - vanillaYaw) * vanillaFollow;
            vanillaPitch += (eyePitch - vanillaPitch) * vanillaFollow;
        } else {
            yaw = targetYaw;
            pitch = targetPitch;
            vanillaYaw = eyeYaw;
            vanillaPitch = eyePitch;
            rotationReady = true;
        }

        float eased = (float) DroneEasing.smootherstep(activation);
        float rawWeight = (1.0f - eased) * (1.0f - eased) * (1.0f - eased);
        float baseYaw = MathHelper.lerpAngleDegrees(rawWeight, vanillaYaw, eyeYaw);
        float basePitch = MathHelper.lerp(rawWeight, vanillaPitch, eyePitch);

        Vec3d blended = eyePos.lerp(placement.position(), eased);
        filming = blended.distanceTo(eyePos) >= FIRST_PERSON_DISTANCE;

        return new DronePose(
            blended.x, blended.y, blended.z,
            MathHelper.lerpAngleDegrees(eased, baseYaw, yaw),
            MathHelper.lerp(eased, basePitch, pitch),
            filming
        );
    }

    private boolean suspended() {
        return Modules.get().isActive(Freecam.class) || Modules.get().isActive(FreeLook.class);
    }

    private void halt() {
        activation = 0.0;
        lastFrameNanos = 0L;
        lastGlideNanos = 0L;
        filming = false;
        rotationReady = false;
        rig.reset();
    }
}
