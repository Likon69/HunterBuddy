package com.hunterbuddy.modules;

import com.hunterbuddy.modules.elytraboost.TakeOffHelper;
import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

/**
 * ElytraBoost — elytra flight via client-side velocity injection.
 *
 * <p>Each tick, while {@code mc.player.isFallFlying()}, intercepts the
 * {@link PlayerMoveEvent} and replaces {@code event.movement} with an
 * interpolated velocity vector aimed at a target speed. The interpolation
 * makes the resulting motion look smoother than a hard velocity set.
 *
 * <p>Distinction: this only modifies local movement vectors before they are
 * sent in the player position packet. It does not send fake rotation/pitch
 * packets to the server.
 *
 * <p>Optional auto take-off / auto-redeploy via {@link TakeOffHelper}.
 */
public class ElytraBoost extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

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
        .description("How fast the current speed interpolates toward the target (0..1).")
        .defaultValue(0.05)
        .min(0.001)
        .sliderMax(1.0)
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

    private double currentSpeed = 0;
    private boolean wasGliding = false;
    private final TakeOffHelper takeOffHelper = new TakeOffHelper();

    public ElytraBoost(Category category) {
        super(category, "elytra-boost",
            "Boost-based elytra flight. Injects a velocity vector on PlayerMoveEvent, optionally with auto-take-off and auto-redeploy.");
    }

    @Override
    public void onActivate() {
        currentSpeed = 0;
        wasGliding = false;
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
    private void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null || !mc.player.isGliding()) return;

        if (pauseInLiquids.get() && (mc.player.isTouchingWater() || mc.player.isInLava())) return;

        // Compute target speed.
        double targetSpeed;
        if (automaticSpeed.get()) {
            float pitch = mc.player.getPitch();
            // Base formula: faster when pitching down. Pitch up slows you down.
            // This is a starting point — refine empirically if you want.
            targetSpeed = 0.3 + Math.max(0, -pitch) * 0.012;
        } else {
            targetSpeed = manualSpeed.get();
        }
        targetSpeed = Math.min(targetSpeed, speedLimit.get());

        // Smooth interpolation: avoid abrupt velocity deltas.
        currentSpeed += (targetSpeed - currentSpeed) * acceleration.get();

        // Client-side pitch clamp only. We do NOT touch outgoing packets here.
        float pitch = mc.player.getPitch();
        float usedPitch = pitchCheck.get() ? Math.max(-89, Math.min(89, pitch)) : pitch;

        // Direction vector: forward (yaw + pitch) or pure look-vector.
        Vec3d direction = useForward.get()
            ? playerForwardVector(mc.player.getYaw(), usedPitch)
            : mc.player.getRotationVector();

        event.movement = direction.multiply(currentSpeed);
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
