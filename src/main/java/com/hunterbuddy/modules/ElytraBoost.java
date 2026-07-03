package com.hunterbuddy.modules;

import com.hunterbuddy.modules.elytraboost.TakeOffHelper;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
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

/**
 * ElytraBoost — elytra flight via client-side velocity injection, with
 * optional server-side pitch spoof.
 *
 * <p>Each tick, while {@code mc.player.isGliding()}, intercepts the
 * {@link PlayerMoveEvent} and replaces {@code event.movement} with an
 * interpolated velocity vector aimed at a target speed. The interpolation
 * (Linear / Exponential / Smoothstep) decides how the speed ramps up to
 * the target.
 *
 * <p>Optional server-side pitch spoof: when enabled, the player's pitch
 * is temporarily swapped to a user-chosen value just before the movement
 * packet is sent (in {@link SendMovementPacketsEvent.Pre}), then restored
 * to the visual value right after (in {@code Post}). The server sees the
 * spoofed pitch and uses it for elytra physics, while the player sees the
 * original pitch.
 *
 * <p>Yaw lock and curve settings are client-side; they don't lie to the
 * server beyond what the locked yaw looks like in the next C2S packet
 * (which any client can do simply by holding the mouse still).
 */
public class ElytraBoost extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public enum YawLockMode {
        Off,
        Forward,    // Lock to player's facing at module-activation.
        Custom      // Lock to fixed-yaw setting.
    }

    public enum AccelCurve {
        Linear,      // constant-rate approach
        Exponential, // fast initial, slow approaching target
        Smoothstep   // smooth at both ends
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
        .defaultValue(0.5)
        .min(0.0)
        .sliderMax(2.0)
        .visible(() -> !automaticSpeed.get())
        .build()
    );

    private final Setting<Double> speedLimit = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed-limit")
        .description("Cap on absolute speed, in blocks/tick.")
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

    // ---- Runtime state ----

    private double currentSpeed = 0;
    private boolean wasGliding = false;
    private float lockedYaw = 0;
    private Float preSpoofPitch = null;  // visual pitch saved between Pre / Post
    private final TakeOffHelper takeOffHelper = new TakeOffHelper();

    public ElytraBoost(Category category) {
        super(category, "elytra-boost",
            "Boost-based elytra flight with optional yaw lock, acceleration curves, and server-side pitch spoof.");
    }

    @Override
    public void onActivate() {
        currentSpeed = 0;
        wasGliding = false;
        // Capture the player's current facing for the "Forward" yaw-lock mode.
        if (mc.player != null) lockedYaw = mc.player.getYaw();
        preSpoofPitch = null;
        if (autoTakeOff.get()) takeOffHelper.start();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Drive the take-off state machine.
        if (takeOffHelper.isRunning()) {
            takeOffHelper.tick();
            return;
        }

        // Auto-redeploy: detect a fresh landing and restart.
        boolean gliding = mc.player.isGliding();
        if (autoRedeploy.get() && wasGliding && !gliding && mc.player.isOnGround()) {
            takeOffHelper.start();
        }
        wasGliding = gliding;
    }

    @EventHandler
    private void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
        if (!isActive() || mc.player == null) return;
        if (!mc.player.isGliding()) return;
        if (!pitchSpoofEnabled.get()) return;

        // Save the visual pitch and swap in the spoofed pitch. The C2S packet
        // is built right after this event, so it carries the spoofed pitch.
        preSpoofPitch = mc.player.getPitch();
        mc.player.setPitch(spoofPitch.get().floatValue());
    }

    @EventHandler
    private void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
        if (preSpoofPitch == null) return;
        // Restore the visual pitch after the packet has been sent.
        if (mc.player != null) mc.player.setPitch(preSpoofPitch);
        preSpoofPitch = null;
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null || !mc.player.isGliding()) return;

        if (pauseInLiquids.get() && (mc.player.isTouchingWater() || mc.player.isInLava())) return;

        // Yaw lock: force the player's yaw before the packet is built.
        switch (yawLock.get()) {
            case Forward -> mc.player.setYaw(lockedYaw);
            case Custom  -> mc.player.setYaw(fixedYaw.get().floatValue());
            case Off     -> { /* let the player rotate freely */ }
        }

        // Compute target speed.
        double targetSpeed;
        if (automaticSpeed.get()) {
            float pitch = mc.player.getPitch();
            // Base formula: faster when pitching down. Pitch up slows you down.
            targetSpeed = 0.3 + Math.max(0, -pitch) * 0.012;
        } else {
            targetSpeed = manualSpeed.get();
        }
        targetSpeed = Math.min(targetSpeed, speedLimit.get());

        // Acceleration curve: pick how the speed approaches target each tick.
        double delta = curveDelta(accelerationCurve.get(), acceleration.get(), targetSpeed, currentSpeed);
        currentSpeed += (targetSpeed - currentSpeed) * delta;

        // Client-side pitch clamp only. Server-side spam is done in Pre/Post events above.
        float pitch = mc.player.getPitch();
        float usedPitch = pitchCheck.get() ? Math.max(-89, Math.min(89, pitch)) : pitch;

        // Direction vector: forward (yaw + pitch) or pure look-vector.
        Vec3d direction = useForward.get()
            ? playerForwardVector(mc.player.getYaw(), usedPitch)
            : mc.player.getRotationVector();

        event.movement = direction.multiply(currentSpeed);
    }

    /**
     * Returns the per-tick interpolation factor (0..1) for the chosen curve.
     *
     * @param curve     The curve type
     * @param a         Acceleration knob (raw setting value, 0..1)
     * @param target    Target speed (block/tick)
     * @param current   Current speed (block/tick)
     */
    private static double curveDelta(AccelCurve curve, double a, double target, double current) {
        switch (curve) {
            case Linear -> {
                return a;
            }
            case Exponential -> {
                // Asymptotic approach: faster when far away, slower as we close in.
                return 1.0 - Math.exp(-a * Math.abs(target - current));
            }
            case Smoothstep -> {
                // Classic ease curve: 3x^2 - 2x^3.
                double x = clamp01(a);
                double s = x * x * (3.0 - 2.0 * x);
                // Also damp when very close to target so we don't overshoot in tight loops.
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
