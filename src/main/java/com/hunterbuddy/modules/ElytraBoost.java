package com.hunterbuddy.modules;

import com.hunterbuddy.modules.elytraboost.MovementNoise;
import com.hunterbuddy.modules.elytraboost.TakeOffHelper;
import com.hunterbuddy.modules.elytraboost.VanillaElytraSimulator;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * ElytraBoost — vanilla-safe elytra flight via client-side velocity injection.
 *
 * <p>Each tick while the player is fall-flying, the module sets
 * {@code mc.player.setVelocity(targetVel)} in {@link TickEvent#Pre} — i.e. before
 * {@code LivingEntity.travel()} runs — so vanilla physics (gravity, lift, glide
 * boost) are applied on top of our velocity rather than replacing it.
 *
 * <p>The target velocity is computed from the player's pitch/yaw and the
 * configured speed model (automatic pitch-based formula or manual speed),
 * then capped against the per-pitch vanilla equilibrium ×
 * {@code vanillaMultiplier} (default 1.0 = identical to vanilla).
 *
 * <p>Anti-detect: Gaussian noise on speed and direction, optional packet
 * timing skips (some ticks run pure vanilla), and optional yaw-lock /
 * pitch-spoof in the SendMovementPackets Pre/Post events.
 */
public class ElytraBoost extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAntiDetect = settings.createGroup("Anti-Detect");

    public enum YawLockMode {
        Off,
        Forward,
        Custom
    }

    public enum AccelCurve {
        Linear,
        Exponential,
        Smoothstep
    }

    // ---- Settings ----

    private final Setting<Boolean> automaticSpeed = sgGeneral.add(new BoolSetting.Builder()
        .name("automatic-speed")
        .description("Adjust speed based on pitch (pitching down = faster).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> manualSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Target speed when automatic-speed is off, in blocks/tick.")
        .defaultValue(1.5)
        .min(0.0)
        .sliderMax(3.0)
        .visible(() -> !automaticSpeed.get())
        .build()
    );

    private final Setting<Double> speedLimit = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-limit")
        .description("Cap on absolute speed, in blocks/tick. Only used when dynamic-speed-cap is OFF.")
        .defaultValue(1.5)
        .min(0.1)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Double> acceleration = sgGeneral.add(new DoubleSetting.Builder()
        .name("acceleration")
        .description("How aggressively the current speed interpolates toward the target. Meaning depends on curve.")
        .defaultValue(0.2)
        .min(0.001)
        .sliderMax(1.0)
        .build()
    );

    private final Setting<AccelCurve> accelerationCurve = sgGeneral.add(new EnumSetting.Builder<AccelCurve>()
        .name("acceleration-curve")
        .description("Interpolation curve for speed ramping.")
        .defaultValue(AccelCurve.Smoothstep)
        .build()
    );

    private final Setting<Boolean> useForward = sgGeneral.add(new BoolSetting.Builder()
        .name("use-forward")
        .description("Use the forward direction (yaw + pitch) instead of the look vector for the motion vector.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pitchCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("pitch-check")
        .description("Client-side pitch clamp (no server-side packet spoofing).")
        .defaultValue(true)
        .build()
    );

    private final Setting<YawLockMode> yawLock = sgGeneral.add(new EnumSetting.Builder<YawLockMode>()
        .name("yaw-lock")
        .description("Lock yaw during flight. Forward captures the player's current facing on activation.")
        .defaultValue(YawLockMode.Off)
        .build()
    );

    private final Setting<Double> fixedYaw = sgGeneral.add(new DoubleSetting.Builder()
        .name("fixed-yaw")
        .description("Yaw (degrees) used when yaw-lock is Custom. 0 = south, -90 = east, 90 = west.")
        .defaultValue(0.0)
        .min(-180.0)
        .max(180.0)
        .sliderRange(-180.0, 180.0)
        .visible(() -> yawLock.get() == YawLockMode.Custom)
        .build()
    );

    private final Setting<Boolean> yawLockVisual = sgGeneral.add(new BoolSetting.Builder()
        .name("yaw-lock-visual")
        .description("Also visually lock the player's yaw each tick. When OFF (default), only the outgoing packet is locked — the camera still rotates freely with the mouse.")
        .defaultValue(false)
        .visible(() -> yawLock.get() != YawLockMode.Off)
        .build()
    );

    private final Setting<Boolean> pitchSpoofEnabled = sgGeneral.add(new BoolSetting.Builder()
        .name("pitch-spoof")
        .description("Send a different pitch to the server than what's rendered. Server gets spoof-pitch; player sees the visual pitch.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> spoofPitch = sgGeneral.add(new IntSetting.Builder()
        .name("spoof-pitch")
        .description("Pitch sent to the server (degrees). + = look down, - = look up. Use around +40 for elytra dive tricks.")
        .defaultValue(40)
        .min(-90)
        .max(90)
        .sliderRange(-90, 90)
        .visible(pitchSpoofEnabled::get)
        .build()
    );

    private final Setting<Boolean> pauseInLiquids = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-in-liquids")
        .description("Disable the boost when touching water or lava.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoRedeploy = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-redeploy")
        .description("Re-take off automatically after touching the ground.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoTakeOff = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-take-off")
        .description("Auto-equip elytra and take off when the module is enabled.")
        .defaultValue(false)
        .build()
    );

    // ---- Anti-Detect ----

    private final Setting<Boolean> useDynamicCap = sgAntiDetect.add(new BoolSetting.Builder()
        .name("dynamic-speed-cap")
        .description("Cap speed based on simulated vanilla elytra physics instead of a fixed limit. Makes speed look legitimate.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> vanillaMultiplier = sgAntiDetect.add(new DoubleSetting.Builder()
        .name("vanilla-multiplier")
        .description("Multiplier over vanilla equilibrium speed. 1.0 = identical to vanilla, 1.5 = 50% faster.")
        .defaultValue(1.0)
        .min(1.0)
        .sliderMax(3.0)
        .visible(useDynamicCap::get)
        .build()
    );

    private final Setting<Boolean> noiseEnabled = sgAntiDetect.add(new BoolSetting.Builder()
        .name("noise")
        .description("Add subtle random noise to the movement vector so it doesn't look perfectly mechanical.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> speedNoise = sgAntiDetect.add(new DoubleSetting.Builder()
        .name("speed-noise")
        .description("Standard deviation for speed noise, as a fraction. 0.02 = ±2% variation per tick.")
        .defaultValue(0.02)
        .min(0.0)
        .sliderMax(0.1)
        .visible(noiseEnabled::get)
        .build()
    );

    private final Setting<Double> directionNoise = sgAntiDetect.add(new DoubleSetting.Builder()
        .name("direction-noise")
        .description("Standard deviation for directional noise, in degrees. 0.5 = ±0.5° per tick.")
        .defaultValue(0.5)
        .min(0.0)
        .sliderMax(3.0)
        .visible(noiseEnabled::get)
        .build()
    );

    private final Setting<Boolean> timingVariation = sgAntiDetect.add(new BoolSetting.Builder()
        .name("timing-variation")
        .description("Randomly skip applying the boost on some ticks, letting vanilla physics run instead.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> skipChance = sgAntiDetect.add(new DoubleSetting.Builder()
        .name("skip-chance")
        .description("Probability of skipping the boost on each tick. 0.05 = ~1 tick in 20 is pure vanilla.")
        .defaultValue(0.05)
        .min(0.0)
        .sliderMax(0.20)
        .visible(timingVariation::get)
        .build()
    );

    // ---- Runtime state ----

    private double currentSpeed = 0;
    private boolean wasGliding = false;
    private float lockedYaw = 0;
    private Float preSpoofPitch = null;
    private Float preLockedYaw = null;
    private final TakeOffHelper takeOffHelper = new TakeOffHelper();
    private final Random rng = new Random();

    public ElytraBoost(Category category) {
        super(category, "elytra-boost",
            "Vanilla-safe elytra speed via setVelocity in TickEvent.Pre. Cap against vanilla equilibrium × multiplier.");
    }

    @Override
    public void onActivate() {
        currentSpeed = 0;
        wasGliding = false;
        if (mc.player != null) lockedYaw = mc.player.getYaw();
        preSpoofPitch = null;
        preLockedYaw = null;
        if (autoTakeOff.get()) takeOffHelper.start();
    }

    @Override
    public void onDeactivate() {
        takeOffHelper.reset();
        preSpoofPitch = null;
        preLockedYaw = null;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (!mc.player.isFallFlying()) return;
        if (takeOffHelper.isRunning()) {
            takeOffHelper.tick();
            return;
        }
        if (pauseInLiquids.get() && (mc.player.isTouchingWater() || mc.player.isInLava())) return;

        // Anti-detect: packet timing variation
        if (timingVariation.get() && rng.nextDouble() < skipChance.get()) return;

        // Yaw lock visual (optional, packet-only by default via Pre/Post events below)
        if (yawLockVisual.get() && yawLock.get() != YawLockMode.Off) {
            float packetYaw = (yawLock.get() == YawLockMode.Forward)
                ? lockedYaw
                : fixedYaw.get().floatValue();
            mc.player.setYaw(packetYaw);
        }

        // ---- Compute target speed ----
        double targetSpeed;
        if (automaticSpeed.get()) {
            float pitch = mc.player.getPitch();
            // Linear approximation of vanilla equilibrium (b/t) by pitch:
            //   pitch -30 -> ~0.5,  pitch 0 -> 1.5,  pitch 30 -> 2.5,  pitch 52 -> 3.2.
            targetSpeed = 1.5 + pitch * 0.033;
        } else {
            targetSpeed = manualSpeed.get();
        }

        // ---- Dynamic speed cap ----
        if (useDynamicCap.get()) {
            double vanillaSpeed = VanillaElytraSimulator.estimateEquilibriumSpeedFast(
                mc.player.getPitch(), mc.player.getYaw());
            double dynamicCap = vanillaSpeed * vanillaMultiplier.get();
            targetSpeed = Math.min(targetSpeed, dynamicCap);
        } else {
            targetSpeed = Math.min(targetSpeed, speedLimit.get());
        }

        // ---- Acceleration curve ----
        double delta = curveDelta(accelerationCurve.get(), acceleration.get(), targetSpeed, currentSpeed, speedLimit.get());
        currentSpeed += (targetSpeed - currentSpeed) * delta;

        // ---- Direction vector ----
        float pitch = mc.player.getPitch();
        float usedPitch = pitchCheck.get() ? Math.max(-89, Math.min(89, pitch)) : pitch;
        Vec3d direction = useForward.get()
            ? playerForwardVector(mc.player.getYaw(), usedPitch)
            : mc.player.getRotationVector();

        Vec3d newVel = direction.multiply(currentSpeed);

        // ---- Anti-Detect: Noise injection ----
        if (noiseEnabled.get()) {
            newVel = MovementNoise.applyNoise(newVel, speedNoise.get(), directionNoise.get(), rng);
        }

        // ---- Override velocity BEFORE LivingEntity.travel() runs ----
        // travel() will then apply gravity/lift on top of this, so the player
        // moves at our velocity + small per-tick physics delta. To maintain
        // altitude at pitch 0, we set motionY = 0 explicitly.
        mc.player.setVelocity(newVel);
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.player == null) return;

        // Auto-redeploy: detect a fresh landing and restart.
        boolean gliding = mc.player.isFallFlying();
        if (autoRedeploy.get() && wasGliding && !gliding && mc.player.isOnGround()) {
            takeOffHelper.start();
        }
        wasGliding = gliding;
    }

    @EventHandler
    private void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        if (!isActive() || mc.player == null || !mc.player.isFallFlying()) return;

        if (yawLock.get() != YawLockMode.Off) {
            preLockedYaw = mc.player.getYaw();
            float packetYaw = (yawLock.get() == YawLockMode.Forward)
                ? lockedYaw
                : fixedYaw.get().floatValue();
            mc.player.setYaw(packetYaw);
        }

        if (pitchSpoofEnabled.get()) {
            preSpoofPitch = mc.player.getPitch();
            mc.player.setPitch(spoofPitch.get().floatValue());
        }
    }

    @EventHandler
    private void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
        if (preLockedYaw != null && mc.player != null) {
            mc.player.setYaw(preLockedYaw);
            preLockedYaw = null;
        }
        if (preSpoofPitch != null && mc.player != null) {
            mc.player.setPitch(preSpoofPitch);
            preSpoofPitch = null;
        }
    }

    private static double curveDelta(AccelCurve curve, double a, double target, double current, double speedLimit) {
        switch (curve) {
            case Linear -> {
                return a;
            }
            case Exponential -> {
                double scale = Math.max(0.001, Math.abs(speedLimit));
                double normalizedDiff = Math.abs(target - current) / scale;
                return 1.0 - Math.exp(-a * normalizedDiff);
            }
            case Smoothstep -> {
                double x = clamp01(a);
                double s = x * x * (3.0 - 2.0 * x);
                double closeFactor = Math.min(1.0, Math.abs(target - current) * 4.0);
                return s * closeFactor;
            }
        }
        return a;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private Vec3d playerForwardVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3d(x, y, z);
    }
}