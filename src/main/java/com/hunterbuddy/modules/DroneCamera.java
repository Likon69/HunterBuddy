package com.hunterbuddy.modules;

import static meteordevelopment.meteorclient.MeteorClient.mc;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.render.DroneCullGuard;
import com.hunterbuddy.render.DroneEasing;
import com.hunterbuddy.render.DronePlacement;
import com.hunterbuddy.render.DronePose;
import com.hunterbuddy.render.DroneRig;
import com.hunterbuddy.render.DroneShot;
import com.hunterbuddy.render.DroneStage;
import com.hunterbuddy.render.DroneTerrain;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.settings.BoolSetting;
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
    private static final double TAKEOFF_ROTATION_TAU = 0.45;
    private static final double ROOM_ROTATION_TAU = 0.6;
    private static final double FOOT_ROTATION_TAU = 0.5;
    private static final double VANILLA_ROTATION_TAU = 0.25;
    private static final float MAX_PITCH = 89.0f;
    private static final float GROUND_DEAD_ZONE = 5.0f;
    private static final float MAX_SUBJECT_ANGLE = 25.0f;
    private static final double MIN_SUBJECT_COSINE = 0.25;
    private static final double CHEST_HEIGHT = 0.4;
    private static final double MIN_YAW_WEIGHT = 0.15;
    private static final double LOOK_MIN_HORIZONTAL = 0.3;
    private static final float HOLD_THIRD_PERSON_WEIGHT = 0.5f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgShots = settings.createGroup("shots");

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

    private final Setting<Boolean> filmWholeRun = sgGeneral.add(new BoolSetting.Builder()
        .name("film-whole-run")
        .description("Keeps filming from outside while the spawner run digs, clears the room and climbs out, instead of returning to first person after the glide.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> filmCombat = sgGeneral.add(new BoolSetting.Builder()
        .name("film-combat")
        .description("Frames both you and your target while the kill aura is fighting.")
        .defaultValue(true)
        .build());

    private final Setting<Double> minSceneSeconds = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-scene-seconds")
        .description("Minimum time between two shot changes caused by the run changing phase or by a fight, so quick flips cannot make the camera jump.")
        .defaultValue(4.0).min(1.0).max(20.0).sliderRange(1.0, 10.0)
        .build());

    private final Map<DroneShot, Setting<Boolean>> shotToggles = new EnumMap<>(DroneShot.class);

    private final DroneRig rig = new DroneRig();
    private final DroneStageSource stageSource = new DroneStageSource();
    private final DroneCullGuard cullGuard = new DroneCullGuard();

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

    {
        for (DroneShot shot : DroneShot.values()) {
            if (shot == DroneShot.TRAIL_CAM) continue;
            String name = shot.name().toLowerCase().replace('_', '-');
            shotToggles.put(shot, sgShots.add(new BoolSetting.Builder()
                .name(name)
                .description("Allows the " + name + " shot.")
                .defaultValue(shot != DroneShot.UNDER_BELLY)
                .build()));
        }
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
        boolean wanted = (lastGlideNanos != 0L && (now - lastGlideNanos) / 1.0e9 < RETURN_DELAY_SECONDS)
            || (filmWholeRun.get() && stageSource.running());

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
        Set<DroneShot> allowed = EnumSet.noneOf(DroneShot.class);
        shotToggles.forEach((shot, toggle) -> {
            if (toggle.get()) allowed.add(shot);
        });
        allowed.add(DroneShot.TRAIL_CAM);
        DroneStage stage = stageSource.read(mc.world, player, focus, dt, tickProgress, filmCombat.get());
        DronePlacement placement = rig.update(mc.world, player, focus, motion, dt, distance.get(), shotSeconds.get(),
            player.isGliding(), allowed, stage, minSceneSeconds.get());

        Vec3d look = placement.lookTarget().subtract(placement.position());
        if (look.length() < MIN_LOOK_DISTANCE) look = rig.flightDirection();

        float targetYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        if (!player.isGliding() && rotationReady && Math.hypot(look.x, look.z) < LOOK_MIN_HORIZONTAL * look.length()) targetYaw = yaw;
        float targetPitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z))), -MAX_PITCH, MAX_PITCH);

        if (rotationReady) {
            float follow = (float) DroneEasing.follow(dt, rig.watchesTakeoff() ? TAKEOFF_ROTATION_TAU : rig.watchesSlowly() ? ROOM_ROTATION_TAU : player.isGliding() ? ROTATION_TAU : FOOT_ROTATION_TAU);
            float vanillaFollow = (float) DroneEasing.follow(dt, VANILLA_ROTATION_TAU);
            float deadZone = player.isGliding() ? 0.0f : GROUND_DEAD_ZONE;
            float yawFollow = follow * (float) Math.max(MIN_YAW_WEIGHT, Math.hypot(look.x, look.z) / look.length());
            yaw += beyond(MathHelper.wrapDegrees(targetYaw - yaw), deadZone) * yawFollow;
            pitch += beyond(targetPitch - pitch, deadZone) * follow;
            keepSubjectInFrame(placement.position(), focus.add(0.0, player.isGliding() ? 0.0 : CHEST_HEIGHT, 0.0));
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
        filming = blended.distanceTo(eyePos) >= FIRST_PERSON_DISTANCE || (rig.holdsThirdPerson() && eased >= HOLD_THIRD_PERSON_WEIGHT);

        cullGuard.update(filming && DroneTerrain.insideBlock(mc.world, blended), dt);

        return new DronePose(
            blended.x, blended.y, blended.z,
            MathHelper.lerpAngleDegrees(eased, baseYaw, yaw),
            MathHelper.lerp(eased, basePitch, pitch),
            filming
        );
    }

    private static float beyond(float error, float deadZone) {
        if (Math.abs(error) <= deadZone) return 0.0f;
        return error > 0.0f ? error - deadZone : error + deadZone;
    }

    private void keepSubjectInFrame(Vec3d camera, Vec3d subject) {
        Vec3d way = subject.subtract(camera);
        double flat = Math.hypot(way.x, way.z);
        if (way.length() < MIN_LOOK_DISTANCE) return;

        float subjectYaw = (float) Math.toDegrees(Math.atan2(-way.x, way.z));
        float subjectPitch = (float) -Math.toDegrees(Math.atan2(way.y, flat));
        float yawLimit = MAX_SUBJECT_ANGLE / (float) Math.max(MIN_SUBJECT_COSINE, flat / way.length());
        float yawError = MathHelper.wrapDegrees(subjectYaw - yaw);
        float pitchError = subjectPitch - pitch;
        yaw += yawError - MathHelper.clamp(yawError, -yawLimit, yawLimit);
        pitch += pitchError - MathHelper.clamp(pitchError, -MAX_SUBJECT_ANGLE, MAX_SUBJECT_ANGLE);
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
        stageSource.reset();
        cullGuard.restore();
        rig.reset();
    }
}
