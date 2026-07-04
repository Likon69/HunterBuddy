package com.hunterbuddy.modules.regear.util;

import com.hunterbuddy.modules.regear.config.MlepConfig;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Reference parity: mlep.util.RotationUtils. Smooth server-side rotations
 * with priority, keepTicks, decay. Single-instance pattern; uses the
 * regear-namespaced MlepConfig.
 */
public class RotationUtils {
    private static RotationUtils INSTANCE;
    public static final double DEFAULT_TURN_SPEED = 45.0;
    public static final double DEFAULT_ALIGN_EPS = 2.0;
    public static final int DEFAULT_DECAY_TICKS = 4;
    private float serverYaw = 0.0F;
    private float serverPitch = 0.0F;
    private Float finalYaw = null;
    private Float finalPitch = null;
    private float currentYaw = 0.0F;
    private float currentPitch = 0.0F;
    private double turnSpeed = 45.0;
    private boolean syncMovement = false;
    private int keepTicks = 0;
    private boolean decaying = false;
    private int decayTicks = 0;
    private boolean initialized = false;
    private Runnable alignedCallback = null;

    private RotationUtils() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    public static RotationUtils getInstance() {
        if (INSTANCE == null) INSTANCE = new RotationUtils();
        return INSTANCE;
    }

    @EventHandler(priority = -200)
    public void onPacketSend(Send event) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.world != null) {
            if (event.packet instanceof PlayerMoveC2SPacket packet && packet.changesLook()) {
                this.serverYaw = packet.getYaw(this.serverYaw);
                this.serverPitch = packet.getPitch(this.serverPitch);
            }
        }
    }

    @EventHandler(priority = 200)
    public void onPacketReceive(Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket packet) {
            // Yarn 1.21.1 exposes the yaw/pitch via the direct getters, not via a
            // `change()` record accessor (the `change()` accessor was added later
            // in Yarn 1.21.4+). Use the direct getters for compatibility.
            this.serverYaw = packet.getYaw();
            this.serverPitch = packet.getPitch();
            this.currentYaw = this.serverYaw;
            this.currentPitch = this.serverPitch;
            this.clearRotations();
        }
    }

    @EventHandler(priority = 200)
    public void onTickPre(Pre event) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.world != null) {
            if (!this.initialized) {
                this.serverYaw = MeteorClient.mc.player.getYaw();
                this.serverPitch = MeteorClient.mc.player.getPitch();
                this.currentYaw = this.serverYaw;
                this.currentPitch = this.serverPitch;
                this.initialized = true;
            }

            if (this.isRotating()) {
                if (this.decaying) {
                    this.finalYaw = MeteorClient.mc.player.getYaw();
                    this.finalPitch = MeteorClient.mc.player.getPitch();
                }
                double yawDiff = MathHelper.clamp(MathHelper.wrapDegrees(this.finalYaw - this.currentYaw), -this.turnSpeed, this.turnSpeed);
                this.currentYaw = (float) (this.currentYaw + yawDiff);
                double pitchDiff = MathHelper.clamp(this.finalPitch - this.currentPitch, -this.turnSpeed, this.turnSpeed);
                this.currentPitch = (float) MathHelper.clamp(this.currentPitch + pitchDiff, -90.0, 90.0);
            }
        }
    }

    @EventHandler(priority = -200)
    public void onTickPost(Post event) {
        if (MeteorClient.mc.player != null && MeteorClient.mc.world != null) {
            if (this.isRotating()) {
                if (this.isAligned() && this.alignedCallback != null) {
                    Runnable callback = this.alignedCallback;
                    this.alignedCallback = null;
                    callback.run();
                }
                if (this.decaying) {
                    if (this.decayTicks-- <= 0 || this.isAligned()) this.clearRotations();
                } else if (this.isAligned()) {
                    if (this.keepTicks > 0) this.keepTicks--;
                    else {
                        this.decaying = true;
                        this.syncMovement = false;
                        this.decayTicks = 4;
                    }
                }
            }
        }
    }

    public void setRotationSilent(float yaw, float pitch) {
        this.setRotationSmooth(yaw, pitch, MlepConfig.getRotationTurnSpeed(), false);
    }

    public void setRotationSilent(float yaw, float pitch, double turnSpeed) {
        this.setRotationSmooth(yaw, pitch, turnSpeed, false);
    }

    public void setRotationFull(float yaw, float pitch) {
        this.setRotationSmooth(yaw, pitch, MlepConfig.getRotationTurnSpeed(), true);
    }

    public void setRotationFull(float yaw, float pitch, double turnSpeed) {
        this.setRotationSmooth(yaw, pitch, turnSpeed, true);
    }

    public void setRotationFullInstant(float yaw, float pitch) {
        this.finalYaw = yaw;
        this.finalPitch = pitch;
        this.currentYaw = yaw;
        this.currentPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        this.turnSpeed = 45.0;
        this.syncMovement = true;
        this.keepTicks = 2;
        this.decaying = false;
    }

    public void sustainRotation(float yaw, float pitch) {
        this.finalYaw = yaw;
        this.finalPitch = pitch;
        this.currentYaw = yaw;
        this.currentPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
        this.turnSpeed = 45.0;
        this.syncMovement = true;
        this.keepTicks = 40;
        this.decaying = false;
    }

    public void setRotationSmooth(float yaw, float pitch, double turnSpeed, boolean sync) {
        if (this.finalYaw == null && this.finalPitch == null) {
            this.currentYaw = this.serverYaw;
            this.currentPitch = this.serverPitch;
        }
        this.finalYaw = yaw;
        this.finalPitch = pitch;
        this.turnSpeed = turnSpeed;
        this.syncMovement = sync;
        this.keepTicks = 2;
        this.decaying = false;
    }

    public void setRotationSilentSync() {
        if (MeteorClient.mc.player != null) {
            this.setRotationSilent(MeteorClient.mc.player.getYaw(), MeteorClient.mc.player.getPitch());
        }
    }

    public void clearRotations() {
        this.finalYaw = null;
        this.finalPitch = null;
        this.syncMovement = false;
        this.keepTicks = 0;
        this.decaying = false;
        this.decayTicks = 0;
        this.alignedCallback = null;
    }

    public void rotateTo(float yaw, float pitch) {
        this.setRotationSilent(yaw, pitch);
    }

    public void rotateTo(float yaw, float pitch, Runnable onAligned) {
        this.setRotationSilent(yaw, pitch);
        this.alignedCallback = onAligned;
    }

    public void rotateTo(float yaw, float pitch, int priority, Runnable onAligned) {
        this.rotateTo(yaw, pitch, onAligned);
    }

    public static void rotate(float yaw, float pitch) {
        getInstance().rotateTo(yaw, pitch);
    }

    public static void rotate(double yaw, double pitch) {
        rotate((float) yaw, (float) pitch);
    }

    public static void rotate(float yaw, float pitch, int priority, Runnable onAligned) {
        getInstance().rotateTo(yaw, pitch, priority, onAligned);
    }

    public static void rotate(double yaw, double pitch, int priority, Runnable onAligned) {
        rotate((float) yaw, (float) pitch, priority, onAligned);
    }

    public static float getYaw(Vec3d target) {
        if (MeteorClient.mc.player == null) return 0.0F;
        return getRotationsTo(MeteorClient.mc.player.getEyePos(), target)[0];
    }

    public static float getPitch(Vec3d target) {
        if (MeteorClient.mc.player == null) return 0.0F;
        return getRotationsTo(MeteorClient.mc.player.getEyePos(), target)[1];
    }

    public static double getYaw(BlockPos pos) {
        return getYaw(Vec3d.ofCenter(pos));
    }

    public boolean isRotating() {
        return this.finalYaw != null || this.finalPitch != null;
    }

    public float getRotationYaw() {
        return this.isRotating() ? this.currentYaw : (MeteorClient.mc.player != null ? MeteorClient.mc.player.getYaw() : 0.0F);
    }

    public float getRotationPitch() {
        return this.isRotating() ? this.currentPitch : (MeteorClient.mc.player != null ? MeteorClient.mc.player.getPitch() : 0.0F);
    }

    public boolean isAligned(double eps) {
        if (!this.isRotating()) return true;
        double dy = this.finalYaw == null ? 0.0 : Math.abs(MathHelper.wrapDegrees(this.finalYaw - this.currentYaw));
        double dp = this.finalPitch == null ? 0.0 : Math.abs(this.finalPitch - this.currentPitch);
        return dy <= eps && dp <= eps;
    }

    public boolean isAligned() {
        return this.isAligned(2.0);
    }

    public float getActiveYaw() { return this.getRotationYaw(); }
    public float getActivePitch() { return this.getRotationPitch(); }

    public Float getMovementPitch() {
        return this.syncMovement && this.isRotating() ? this.currentPitch : null;
    }

    public Float getMovementYaw() {
        return this.syncMovement && this.isRotating() ? this.currentYaw : null;
    }

    public float getServerYaw() { return this.serverYaw; }
    public float getServerPitch() { return this.serverPitch; }
    public float getWrappedYaw() { return MathHelper.wrapDegrees(this.serverYaw); }

    public static float[] getRotationsTo(Vec3d src, Vec3d dest) {
        double diffX = dest.x - src.x;
        double diffY = dest.y - src.y;
        double diffZ = dest.z - src.z;
        double yaw = Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0;
        double pitch = -Math.toDegrees(Math.atan2(diffY, Math.hypot(diffX, diffZ)));
        return new float[]{MathHelper.wrapDegrees((float) yaw), MathHelper.wrapDegrees((float) pitch)};
    }

    public static float[] getRotationsTo(Entity entity, HitVector hitVector) {
        if (MeteorClient.mc.player == null) return new float[]{0.0F, 0.0F};
        Vec3d targetPos = getHitVector(entity, hitVector);
        return getRotationsTo(MeteorClient.mc.player.getEyePos(), targetPos);
    }

    public static Vec3d getHitVector(Entity entity, HitVector hitVector) {
        // Yarn 1.21.1 uses `getPos()` to return the entity's Vec3d position
        // (older Yarn mappings had a separate `getEntityPos()` alias).
        Vec3d feetPos = entity.getPos();
        return switch (hitVector) {
            case FEET -> feetPos;
            case TORSO -> feetPos.add(0.0, entity.getHeight() / 2.0F, 0.0);
            case EYES -> entity.getEyePos();
            case CLOSEST -> {
                if (MeteorClient.mc.player == null) yield feetPos;
                Vec3d eyePos = MeteorClient.mc.player.getEyePos();
                Vec3d torsoPos = feetPos.add(0.0, entity.getHeight() / 2.0F, 0.0);
                Vec3d eyesPos = entity.getEyePos();
                double feetDist = eyePos.squaredDistanceTo(feetPos);
                double torsoDist = eyePos.squaredDistanceTo(torsoPos);
                double eyesDist = eyePos.squaredDistanceTo(eyesPos);
                yield feetDist <= torsoDist && feetDist <= eyesDist ? feetPos : (torsoDist <= eyesDist ? torsoPos : eyesPos);
            }
        };
    }

    public static Vec3d getRotationVector(float pitch, float yaw) {
        float pitchRad = pitch * (float) (Math.PI / 180.0);
        float yawRad = -yaw * (float) (Math.PI / 180.0);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        return new Vec3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    public static boolean isInFov(Vec3d from, Vec3d to, float fov) {
        if (fov >= 180.0F || MeteorClient.mc.player == null) return fov >= 180.0F;
        float[] rotations = getRotationsTo(from, to);
        float yawDiff = MathHelper.wrapDegrees(MeteorClient.mc.player.getYaw() - rotations[0]);
        return Math.abs(yawDiff) <= fov;
    }

    public static double getAngleDifference(float a, float b) {
        return ((a - b) % 360.0 + 540.0) % 360.0 - 180.0;
    }

    @Deprecated
    public void requestRotation(double yaw, double pitch, RotationConfig config) {
        this.setRotationSilent((float) yaw, (float) pitch);
    }

    @Deprecated
    public void setRotation(Rotation rotation) {
        this.setRotationSilent(rotation.getYaw(), rotation.getPitch());
    }

    @Deprecated
    public void setRotationClient(float yaw, float pitch) {
        if (MeteorClient.mc.player != null) {
            MeteorClient.mc.player.setYaw(yaw);
            MeteorClient.mc.player.setPitch(MathHelper.clamp(pitch, -90.0F, 90.0F));
        }
    }

    public enum HitVector { FEET, TORSO, EYES, CLOSEST; }

    @Deprecated
    public static class Rotation {
        private final int priority;
        private float yaw;
        private float pitch;
        private boolean snap;
        public Rotation(int priority, float yaw, float pitch, boolean snap) {
            this.priority = priority; this.yaw = yaw; this.pitch = pitch; this.snap = snap;
        }
        public Rotation(int priority, float yaw, float pitch) { this(priority, yaw, pitch, false); }
        public int getPriority() { return priority; }
        public void setYaw(float yaw) { this.yaw = yaw; }
        public void setPitch(float pitch) { this.pitch = pitch; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
        public void setSnap(boolean snap) { this.snap = snap; }
        public boolean isSnap() { return snap; }
    }

    @Deprecated
    public static class RotationConfig {
        private RotationMode mode = RotationMode.SILENT;
        private double turnSpeed = 180.0;
        private int keepTicks = 1;
        private int decayTicks = 0;
        private int priority = 0;
        public RotationConfig mode(RotationMode mode) { this.mode = mode; return this; }
        public RotationConfig turnSpeed(double v) { this.turnSpeed = v; return this; }
        public RotationConfig keepTicks(int v) { this.keepTicks = v; return this; }
        public RotationConfig decayTicks(int v) { this.decayTicks = v; return this; }
        public RotationConfig priority(int v) { this.priority = v; return this; }
        public RotationMode getMode() { return mode; }
        public double getTurnSpeed() { return turnSpeed; }
        public int getKeepTicks() { return keepTicks; }
        public int getDecayTicks() { return decayTicks; }
        public int getPriority() { return priority; }
    }

    public enum RotationMode { SILENT, SYNC, LOCK; }
}