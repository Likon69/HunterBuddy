package com.hunterbuddy.modules.regear.util;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class RotationUtils {
    public static final double DEFAULT_TURN_SPEED = 45.0;
    public static final double DEFAULT_ALIGN_EPS = 2.0;
    public static final int DEFAULT_DECAY_TICKS = 4;

    private static final float MAX_PITCH = 90.0f;
    private static final int KEEP_TICKS_SHORT = 2;
    private static final int KEEP_TICKS_SUSTAINED = 40;

    private static RotationUtils instance;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private float serverYaw;
    private float serverPitch;
    private float currentYaw;
    private float currentPitch;
    private float targetYaw;
    private float targetPitch;
    private boolean rotating;
    private double turnSpeed = DEFAULT_TURN_SPEED;
    private boolean syncMovement;
    private int keepTicks;
    private boolean decaying;
    private int decayTicks;
    private boolean initialized;
    private Runnable alignedCallback;

    private RotationUtils() {
    }

    public static RotationUtils getInstance() {
        if (instance == null) {
            instance = new RotationUtils();
            MeteorClient.EVENT_BUS.subscribe(instance);
        }
        return instance;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket move && move.changesLook()) {
            serverYaw = move.getYaw(serverYaw);
            serverPitch = move.getPitch(serverPitch);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket correction) {
            serverYaw = correction.change().yaw();
            serverPitch = correction.change().pitch();
            currentYaw = serverYaw;
            currentPitch = serverPitch;
            clearRotations();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        initializeFromPlayer();
        if (!rotating) return;
        if (decaying) {
            targetYaw = mc.player.getYaw();
            targetPitch = mc.player.getPitch();
        }
        stepTowardsTarget();
    }

    private void initializeFromPlayer() {
        if (initialized) return;
        initialized = true;
        serverYaw = mc.player.getYaw();
        serverPitch = mc.player.getPitch();
        currentYaw = serverYaw;
        currentPitch = serverPitch;
    }

    private void stepTowardsTarget() {
        float yawStep = (float) MathHelper.clamp(MathHelper.wrapDegrees(targetYaw - currentYaw), -turnSpeed, turnSpeed);
        float pitchStep = (float) MathHelper.clamp(targetPitch - currentPitch, -turnSpeed, turnSpeed);
        currentYaw += yawStep;
        currentPitch = MathHelper.clamp(currentPitch + pitchStep, -MAX_PITCH, MAX_PITCH);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onTickPost(TickEvent.Post event) {
        if (!rotating) return;
        boolean aligned = isAligned(DEFAULT_ALIGN_EPS);
        runAlignedCallback(aligned);
        if (!rotating) return;
        if (decaying) {
            tickDecay(aligned);
        } else if (aligned) {
            tickKeep();
        }
    }

    private void runAlignedCallback(boolean aligned) {
        if (!aligned || alignedCallback == null) return;
        Runnable callback = alignedCallback;
        alignedCallback = null;
        callback.run();
    }

    private void tickDecay(boolean aligned) {
        boolean expired = decayTicks <= 0;
        decayTicks--;
        if (expired || aligned) {
            clearRotations();
        }
    }

    private void tickKeep() {
        if (keepTicks > 0) {
            keepTicks--;
        }
        if (keepTicks > 0) return;
        decaying = true;
        syncMovement = false;
        decayTicks = DEFAULT_DECAY_TICKS;
    }

    public void setRotationSmooth(float yaw, float pitch, double speed, boolean sync) {
        if (!rotating) {
            currentYaw = serverYaw;
            currentPitch = serverPitch;
        }
        rotating = true;
        targetYaw = yaw;
        targetPitch = MathHelper.clamp(pitch, -MAX_PITCH, MAX_PITCH);
        turnSpeed = speed;
        syncMovement = sync;
        keepTicks = KEEP_TICKS_SHORT;
        decaying = false;
    }

    public void setRotationSilent(float yaw, float pitch) {
        setRotationSmooth(yaw, pitch, DEFAULT_TURN_SPEED, false);
    }

    public void setRotationSilent(float yaw, float pitch, double speed) {
        setRotationSmooth(yaw, pitch, speed, false);
    }

    public void setRotationFull(float yaw, float pitch) {
        setRotationSmooth(yaw, pitch, DEFAULT_TURN_SPEED, true);
    }

    public void setRotationFull(float yaw, float pitch, double speed) {
        setRotationSmooth(yaw, pitch, speed, true);
    }

    public void setRotationFullInstant(float yaw, float pitch) {
        snapTo(yaw, pitch, KEEP_TICKS_SHORT);
    }

    public void sustainRotation(float yaw, float pitch) {
        snapTo(yaw, pitch, KEEP_TICKS_SUSTAINED);
    }

    private void snapTo(float yaw, float pitch, int keep) {
        float clampedPitch = MathHelper.clamp(pitch, -MAX_PITCH, MAX_PITCH);
        rotating = true;
        targetYaw = yaw;
        targetPitch = clampedPitch;
        currentYaw = yaw;
        currentPitch = clampedPitch;
        turnSpeed = DEFAULT_TURN_SPEED;
        syncMovement = true;
        keepTicks = keep;
        decaying = false;
    }

    public void setRotationSilentSync() {
        if (mc.player == null) return;
        setRotationSilent(mc.player.getYaw(), mc.player.getPitch());
    }

    public void clearRotations() {
        rotating = false;
        syncMovement = false;
        keepTicks = 0;
        decayTicks = 0;
        decaying = false;
        alignedCallback = null;
    }

    public void rotateTo(float yaw, float pitch) {
        setRotationSilent(yaw, pitch);
    }

    public void rotateTo(float yaw, float pitch, Runnable onAligned) {
        setRotationSilent(yaw, pitch);
        alignedCallback = onAligned;
    }

    public void rotateTo(float yaw, float pitch, int priority, Runnable onAligned) {
        rotateTo(yaw, pitch, onAligned);
    }

    public boolean isRotating() {
        return rotating;
    }

    public float getRotationYaw() {
        if (rotating) return currentYaw;
        return mc.player != null ? mc.player.getYaw() : 0.0f;
    }

    public float getRotationPitch() {
        if (rotating) return currentPitch;
        return mc.player != null ? mc.player.getPitch() : 0.0f;
    }

    public float getActiveYaw() {
        return getRotationYaw();
    }

    public float getActivePitch() {
        return getRotationPitch();
    }

    public boolean isAligned() {
        return isAligned(DEFAULT_ALIGN_EPS);
    }

    public boolean isAligned(double eps) {
        if (!rotating) return true;
        return Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw)) <= eps
            && Math.abs(targetPitch - currentPitch) <= eps;
    }

    public Float getMovementYaw() {
        return syncMovement && rotating ? Float.valueOf(currentYaw) : null;
    }

    public Float getMovementPitch() {
        return syncMovement && rotating ? Float.valueOf(currentPitch) : null;
    }

    public float getServerYaw() {
        return serverYaw;
    }

    public float getServerPitch() {
        return serverPitch;
    }

    public float getWrappedYaw() {
        return MathHelper.wrapDegrees(serverYaw);
    }

    @Deprecated
    public void requestRotation(double yaw, double pitch, RotationConfig config) {
        setRotationSilent((float) yaw, (float) pitch);
    }

    @Deprecated
    public void setRotation(Rotation rotation) {
        setRotationSilent(rotation.getYaw(), rotation.getPitch());
    }

    @Deprecated
    public void setRotationClient(float yaw, float pitch) {
        if (mc.player == null) return;
        mc.player.setYaw(yaw);
        mc.player.setPitch(MathHelper.clamp(pitch, -MAX_PITCH, MAX_PITCH));
    }

    public static void rotate(float yaw, float pitch) {
        getInstance().rotateTo(yaw, pitch);
    }

    public static void rotate(double yaw, double pitch) {
        getInstance().rotateTo((float) yaw, (float) pitch);
    }

    public static void rotate(float yaw, float pitch, int priority, Runnable onAligned) {
        getInstance().rotateTo(yaw, pitch, priority, onAligned);
    }

    public static void rotate(double yaw, double pitch, int priority, Runnable onAligned) {
        getInstance().rotateTo((float) yaw, (float) pitch, priority, onAligned);
    }

    public static float getYaw(Vec3d target) {
        return AimMath.yawTo(target);
    }

    public static float getPitch(Vec3d target) {
        return AimMath.pitchTo(target);
    }

    public static double getYaw(BlockPos pos) {
        return AimMath.yawTo(Vec3d.ofCenter(pos));
    }

    public static float[] getRotationsTo(Vec3d src, Vec3d dest) {
        return AimMath.rotationsBetween(src, dest);
    }

    public static float[] getRotationsTo(Entity entity, HitVector hit) {
        return AimMath.rotationsToEntity(entity, hit);
    }

    public static Vec3d getHitVector(Entity entity, HitVector hit) {
        return AimMath.hitPoint(entity, hit);
    }

    public static Vec3d getRotationVector(float pitch, float yaw) {
        return AimMath.lookVector(pitch, yaw);
    }

    public static boolean isInFov(Vec3d from, Vec3d to, float fov) {
        return AimMath.isWithinFov(from, to, fov);
    }

    public static double getAngleDifference(float a, float b) {
        return AimMath.angleDifference(a, b);
    }

    public enum HitVector {
        FEET,
        TORSO,
        EYES,
        CLOSEST
    }

    public enum RotationMode {
        SILENT,
        SYNC,
        LOCK
    }

    @Deprecated
    public static class Rotation {
        private final int priority;
        private float yaw;
        private float pitch;
        private boolean snap;

        public Rotation(int priority, float yaw, float pitch, boolean snap) {
            this.priority = priority;
            this.yaw = yaw;
            this.pitch = pitch;
            this.snap = snap;
        }

        public Rotation(int priority, float yaw, float pitch) {
            this(priority, yaw, pitch, false);
        }

        public int getPriority() {
            return priority;
        }

        public float getYaw() {
            return yaw;
        }

        public float getPitch() {
            return pitch;
        }

        public boolean isSnap() {
            return snap;
        }

        public void setYaw(float yaw) {
            this.yaw = yaw;
        }

        public void setPitch(float pitch) {
            this.pitch = pitch;
        }

        public void setSnap(boolean snap) {
            this.snap = snap;
        }
    }

    public static class RotationConfig {
        private RotationMode mode = RotationMode.SILENT;
        private double turnSpeed = 180.0;
        private int keepTicks = 1;
        private int decayTicks = 0;
        private int priority = 0;

        public RotationConfig mode(RotationMode mode) {
            this.mode = mode;
            return this;
        }

        public RotationConfig turnSpeed(double turnSpeed) {
            this.turnSpeed = turnSpeed;
            return this;
        }

        public RotationConfig keepTicks(int keepTicks) {
            this.keepTicks = keepTicks;
            return this;
        }

        public RotationConfig decayTicks(int decayTicks) {
            this.decayTicks = decayTicks;
            return this;
        }

        public RotationConfig priority(int priority) {
            this.priority = priority;
            return this;
        }

        public RotationMode getMode() {
            return mode;
        }

        public double getTurnSpeed() {
            return turnSpeed;
        }

        public int getKeepTicks() {
            return keepTicks;
        }

        public int getDecayTicks() {
            return decayTicks;
        }

        public int getPriority() {
            return priority;
        }
    }
}
