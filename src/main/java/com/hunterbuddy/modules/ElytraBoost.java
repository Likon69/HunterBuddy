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
 * Vanilla-safe elytra cruise. Sets the player's velocity to the vanilla
 * equilibrium vector (computed from {@link VanillaElytraSimulator}) scaled
 * by {@code vanillaMultiplier}, BEFORE {@code LivingEntity.travel()} runs.
 *
 * <p><b>GrimAC reality:</b> 2b2t runs a fork of GrimAC that validates every
 * position update against a server-side vanilla physics simulation. Tolerance
 * is ~0.03 blocks (floating-point precision, NOT a speed buffer). Any
 * deviation > 0.03 between client position and simulated vanilla triggers a
 * rubberband. To stay safe:
 * <ul>
 *   <li>{@code vanillaMultiplier} must stay ≤ 1.0. Higher values produce
 *       positions unreachable by vanilla physics → rollback.</li>
 *   <li>Speed noise per tick must stay below 0.03 blocks. {@code speedNoise}
 *       default is 0.01 (σ ≈ 1.5% of speed) — 0.02 was the original value but
 *       its σ ≈ 0.03 is right at the tolerance limit, hence the rollback.</li>
 *   <li>Some ticks ({@code skipChance} default 5%) let vanilla physics run
 *       un-overridden, breaking the "perfectly mechanical" pattern that
 *       itself looks suspicious.</li>
 * </ul>
 *
 * <p>Pitch spoof and yaw lock are handled in {@code SendMovementPackets}
 * Pre/Post events (packet-only by default).
 */
public class ElytraBoost extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAntiDetect = settings.createGroup("Anti-Detect");

    public enum YawLockMode { Off, Forward, Custom }
    public enum AccelCurve { Linear, Exponential, Smoothstep }

    // ---- General ----

    private final Setting<Double> acceleration = sgGeneral.add(new DoubleSetting.Builder()
        .name("acceleration")
        .description("How aggressively current speed interpolates toward the vanilla equilibrium. Meaning depends on curve.")
        .defaultValue(0.2)
        .min(0.001)
        .sliderMax(1.0)
        .build()
    );

    private final Setting<AccelCurve> accelerationCurve = sgGeneral.add(new EnumSetting.Builder<AccelCurve>()
        .name("acceleration-curve")
        .description("Interpolation curve for the startup ramp toward equilibrium speed.")
        .defaultValue(AccelCurve.Smoothstep)
        .build()
    );

    private final Setting<Boolean> pitchCheck = sgGeneral.add(new BoolSetting.Builder()
        .name("pitch-check")
        .description("Clamp pitch to [-89, +89] before computing the look vector. Avoids look-vector singularities.")
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
        .description("Yaw (degrees) when yaw-lock is Custom. 0 = south, -90 = east, 90 = west.")
        .defaultValue(0.0)
        .min(-180.0)
        .max(180.0)
        .sliderRange(-180.0, 180.0)
        .visible(() -> yawLock.get() == YawLockMode.Custom)
        .build()
    );

    private final Setting<Boolean> yawLockVisual = sgGeneral.add(new BoolSetting.Builder()
        .name("yaw-lock-visual")
        .description("Also visually lock the player's yaw. OFF = packet-only yaw lock; the camera still rotates with the mouse.")
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
        .description("Pitch sent to the server (degrees). + = down, - = up.")
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

    private final Setting<Boolean> useEquilibriumVelocity = sgAntiDetect.add(new BoolSetting.Builder()
        .name("equilibrium-velocity")
        .description("Use the simulated vanilla equilibrium VECTOR as the velocity override (vanilla-safe). When OFF, falls back to a simpler formula that's faster but rollback-prone.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> vanillaMultiplier = sgAntiDetect.add(new DoubleSetting.Builder()
        .name("vanilla-multiplier")
        .description("Multiplier over the vanilla equilibrium speed. 1.0 = identical to vanilla. WARNING: anything > 1.0 produces positions unreachable by vanilla physics and GrimAC will rubberband.")
        .defaultValue(1.0)
        .min(1.0)
        .sliderMax(3.0)
        .build()
    );

    private final Setting<Boolean> noiseEnabled = sgAntiDetect.add(new BoolSetting.Builder()
        .name("noise")
        .description("Subtle random noise on the velocity vector. σ is fraction of speed (e.g. 0.01 = 1%).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> speedNoise = sgAntiDetect.add(new DoubleSetting.Builder()
        .name("speed-noise")
        .description("Speed noise σ as a fraction. Per-tick position error ≈ speed × σ × 20. Stay below 0.03/20 ≈ 0.0015 to never exceed GrimAC's 0.03 tolerance — 0.01 gives σ ≈ 0.015 (safe).")
        .defaultValue(0.01)
        .min(0.0)
        .sliderMax(0.05)
        .visible(noiseEnabled::get)
        .build()
    );

    private final Setting<Double> directionNoise = sgAntiDetect.add(new DoubleSetting.Builder()
        .name("direction-noise")
        .description("Directional noise σ in degrees. Subtle micro-corrections to look more human.")
        .defaultValue(0.3)
        .min(0.0)
        .sliderMax(3.0)
        .visible(noiseEnabled::get)
        .build()
    );

    private final Setting<Boolean> timingVariation = sgAntiDetect.add(new BoolSetting.Builder()
        .name("timing-variation")
        .description("Randomly skip applying the boost on some ticks. Vanilla physics runs instead, breaking the 'always-equilibrium' pattern.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> skipChance = sgAntiDetect.add(new DoubleSetting.Builder()
        .name("skip-chance")
        .description("Probability of skipping the boost on each tick. 0.05 ≈ 1 tick in 20 is pure vanilla.")
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
            "Vanilla-safe elytra cruise via equilibrium-velocity setVelocity. Anti-GrimAC: subtle noise + skip-ticks. WARNING: keep vanilla-multiplier at 1.0.");
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

        // Anti-detect: skip-tick — let vanilla physics run unmodified this tick.
        // This is critical because always-overriding with the same equilibrium
        // vector produces a perfectly mechanical trajectory that GrimAC flags
        // as suspicious on its own.
        if (timingVariation.get() && rng.nextDouble() < skipChance.get()) return;

        // Yaw lock visual (optional).
        if (yawLockVisual.get() && yawLock.get() != YawLockMode.Off) {
            float packetYaw = (yawLock.get() == YawLockMode.Forward)
                ? lockedYaw
                : fixedYaw.get().floatValue();
            mc.player.setYaw(packetYaw);
        }

        float pitch = mc.player.getPitch();
        float yaw = mc.player.getYaw();
        float usedPitch = pitchCheck.get() ? Math.max(-89, Math.min(89, pitch)) : pitch;

        // ---- Compute target velocity ----
        // Hard-cap vanillaMultiplier at 1.0: anything higher produces positions
        // unreachable by vanilla physics, which GrimAC's per-tick Simulation
        // check will flag with a rubberband.
        double multiplier = Math.min(vanillaMultiplier.get(), 1.0);
        Vec3d targetVel;
        if (useEquilibriumVelocity.get()) {
            // Exact vanilla equilibrium (Vec3d with proper X, Y, Z). Matches
            // what GrimAC's simulation produces, so per-tick position error is
            // essentially zero.
            Vec3d equilibrium = VanillaElytraSimulator.calculateEquilibriumVelocity(usedPitch, yaw);
            targetVel = equilibrium.multiply(multiplier);
        } else {
            // Fallback: faster but rollback-prone. direction × speed, no Y
            // component — player loses altitude. Not recommended on 2b2t.
            double speed = VanillaElytraSimulator.estimateEquilibriumSpeedFast(usedPitch, yaw) * multiplier;
            targetVel = playerForwardVector(yaw, usedPitch).multiply(speed);
        }

        // ---- Acceleration ramp (startup smoothing) ----
        // Ramp currentSpeed toward targetSpeed so the module doesn't snap the
        // player to full speed on activation. After the ramp (a few seconds),
        // currentSpeed ≈ targetSpeed and we just track equilibrium.
        double targetSpeed = targetVel.length();
        double delta = curveDelta(accelerationCurve.get(), acceleration.get(), targetSpeed, currentSpeed, targetSpeed);
        currentSpeed += (targetSpeed - currentSpeed) * delta;
        if (targetVel.length() > 1e-6) {
            double scale = currentSpeed / targetVel.length();
            // Don't overshoot equilibrium (clamp to ≤ multiplier × eq speed)
            scale = Math.min(scale, 1.0);
            targetVel = targetVel.multiply(scale);
        }

        // ---- Anti-detect: subtle noise ----
        // Per-tick position error from speed noise is roughly
        //   σ_pos = speed × σ_speed × 20  (b/t × fraction × ticks/s).
        // At default speed=1.5 and σ=0.01 → σ_pos ≈ 0.3 b/s. Per-tick std
        // ≈ 0.015. GrimAC tolerance is 0.03 — we're inside.
        if (noiseEnabled.get()) {
            targetVel = MovementNoise.applyNoise(targetVel, speedNoise.get(), directionNoise.get(), rng);
        }

        mc.player.setVelocity(targetVel);
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.player == null) return;

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
            case Linear -> { return a; }
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