package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;

public class BoostLab extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgLogging = settings.createGroup("Logging");

    private final Setting<BoostMode> mode = sgGeneral.add(new EnumSetting.Builder<BoostMode>()
        .name("mode")
        .description("Boost experiment to run.")
        .defaultValue(BoostMode.PitchOptimizer)
        .build()
    );

    private final Setting<Boolean> allowUnsafeVelocity = sgSafety.add(new BoolSetting.Builder()
        .name("allow-unsafe-velocity")
        .description("Allow direct client velocity edits. Grim is expected to detect these; use only for limit testing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> horizontalMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-multiplier")
        .description("Multiplies horizontal velocity. Keep tiny for live testing.")
        .defaultValue(1.005)
        .min(1.0)
        .max(1.1)
        .sliderRange(1.0, 1.05)
        .visible(() -> allowUnsafeVelocity.get() && mode.get() == BoostMode.MultiplyHorizontal)
        .build()
    );

    private final Setting<Double> addSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("add-speed")
        .description("Adds velocity in the current yaw direction.")
        .defaultValue(0.005)
        .min(0.0)
        .max(0.08)
        .sliderRange(0.0, 0.03)
        .visible(() -> allowUnsafeVelocity.get() && mode.get() == BoostMode.AddYawVelocity)
        .build()
    );

    private final Setting<Double> targetPitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-pitch")
        .description("Pitch to hold in pitch optimizer mode.")
        .defaultValue(35.0)
        .min(-80.0)
        .max(80.0)
        .sliderRange(-60.0, 60.0)
        .visible(() -> mode.get() == BoostMode.PitchOptimizer)
        .build()
    );

    private final Setting<Integer> pulseTicks = sgGeneral.add(new IntSetting.Builder()
        .name("pulse-ticks")
        .description("Apply boost once every N ticks.")
        .defaultValue(5)
        .min(1)
        .max(40)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> rampTicks = sgGeneral.add(new IntSetting.Builder()
        .name("ramp-ticks")
        .description("Ticks to ramp from no boost to configured boost.")
        .defaultValue(120)
        .min(0)
        .max(1200)
        .sliderRange(0, 400)
        .build()
    );

    private final Setting<Boolean> onlyGliding = sgSafety.add(new BoolSetting.Builder()
        .name("only-gliding")
        .description("Only run while elytra gliding.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireForward = sgSafety.add(new BoolSetting.Builder()
        .name("require-forward")
        .description("Only boost while forward is pressed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> maxHorizontalBps = sgSafety.add(new DoubleSetting.Builder()
        .name("max-horizontal-bps")
        .description("Do not apply boost when horizontal speed is above this value.")
        .defaultValue(80.0)
        .min(10.0)
        .max(250.0)
        .sliderRange(20.0, 140.0)
        .build()
    );

    private final Setting<Double> minY = sgSafety.add(new DoubleSetting.Builder()
        .name("min-y")
        .description("Do not apply boost below this Y level.")
        .defaultValue(-64.0)
        .min(-64.0)
        .max(320.0)
        .sliderRange(-64.0, 320.0)
        .build()
    );

    private final Setting<Boolean> autoDisableOnCorrection = sgSafety.add(new BoolSetting.Builder()
        .name("disable-on-correction")
        .description("Disable when the server sends a position correction.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> localCorrectionDistance = sgSafety.add(new DoubleSetting.Builder()
        .name("local-correction-distance")
        .description("Disable if one tick moves farther than this without a normal boost explanation.")
        .defaultValue(12.0)
        .min(3.0)
        .max(100.0)
        .sliderRange(3.0, 30.0)
        .build()
    );

    private final Setting<Boolean> chatLogs = sgLogging.add(new BoolSetting.Builder()
        .name("chat-logs")
        .description("Print periodic speed and correction stats.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> logIntervalTicks = sgLogging.add(new IntSetting.Builder()
        .name("log-interval-ticks")
        .description("Ticks between chat log lines.")
        .defaultValue(200)
        .min(20)
        .max(2400)
        .sliderRange(100, 600)
        .visible(chatLogs::get)
        .build()
    );

    private int ticks;
    private int applied;
    private int serverCorrections;
    private double lastHorizontalBps;
    private double bestHorizontalBps;
    private Vec3d lastPos;
    private boolean warnedUnsafeBlocked;

    public BoostLab() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "boost-lab", "Controlled elytra boost experiments with correction detection.");
    }

    @Override
    public void onActivate() {
        ticks = 0;
        applied = 0;
        serverCorrections = 0;
        lastHorizontalBps = 0.0;
        bestHorizontalBps = 0.0;
        lastPos = mc.player != null ? mc.player.getEntityPos() : null;
        warnedUnsafeBlocked = false;
    }

    @Override
    public void onDeactivate() {
        lastPos = null;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            serverCorrections++;
            if (autoDisableOnCorrection.get()) {
                warning("Server position correction detected. Disabling BoostLab. Applied=%d Best=%.1f b/s", applied, bestHorizontalBps);
                toggle();
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        ticks++;
        updateSpeedStats();

        if (shouldDisableForLocalCorrection()) {
            warning("Large local movement jump detected. Disabling BoostLab. Applied=%d Best=%.1f b/s", applied, bestHorizontalBps);
            toggle();
            return;
        }

        if (!canApply()) return;
        if (ticks % pulseTicks.get() != 0) return;

        switch (mode.get()) {
            case MultiplyHorizontal -> multiplyHorizontalVelocity();
            case AddYawVelocity -> addYawVelocity();
            case PitchOptimizer -> mc.player.setPitch(targetPitch.get().floatValue());
        }

        applied++;

        if (chatLogs.get() && ticks % logIntervalTicks.get() == 0) {
            info("mode=%s speed=%.1f best=%.1f applied=%d corrections=%d ramp=%.2f",
                mode.get(), lastHorizontalBps, bestHorizontalBps, applied, serverCorrections, ramp());
        }
    }

    private boolean canApply() {
        if (onlyGliding.get() && !mc.player.isGliding()) return false;
        if (requireForward.get() && !mc.options.forwardKey.isPressed()) return false;
        if (mc.player.getY() < minY.get()) return false;
        if (usesUnsafeVelocity() && !allowUnsafeVelocity.get()) {
            if (!warnedUnsafeBlocked) {
                warning("Unsafe velocity mode blocked. Enable allow-unsafe-velocity only for controlled testing.");
                warnedUnsafeBlocked = true;
            }
            return false;
        }
        return lastHorizontalBps <= maxHorizontalBps.get();
    }

    private boolean usesUnsafeVelocity() {
        return mode.get() == BoostMode.MultiplyHorizontal || mode.get() == BoostMode.AddYawVelocity;
    }

    private void updateSpeedStats() {
        Vec3d pos = mc.player.getEntityPos();
        if (lastPos != null) {
            double dx = pos.x - lastPos.x;
            double dz = pos.z - lastPos.z;
            lastHorizontalBps = Math.sqrt(dx * dx + dz * dz) * 20.0;
            bestHorizontalBps = Math.max(bestHorizontalBps, lastHorizontalBps);
        }
        lastPos = pos;
    }

    private boolean shouldDisableForLocalCorrection() {
        return lastHorizontalBps > localCorrectionDistance.get() * 20.0 && serverCorrections > 0;
    }

    private double ramp() {
        int ramp = rampTicks.get();
        if (ramp <= 0) return 1.0;
        return Math.min(1.0, ticks / (double)ramp);
    }

    private void multiplyHorizontalVelocity() {
        Vec3d velocity = mc.player.getVelocity();
        double multiplier = 1.0 + (horizontalMultiplier.get() - 1.0) * ramp();
        mc.player.setVelocity(velocity.x * multiplier, velocity.y, velocity.z * multiplier);
    }

    private void addYawVelocity() {
        float yaw = (float)Math.toRadians(mc.player.getYaw());
        double boost = addSpeed.get() * ramp();
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(
            velocity.x - Math.sin(yaw) * boost,
            velocity.y,
            velocity.z + Math.cos(yaw) * boost
        );
    }

    @Override
    public String getInfoString() {
        return String.format("%.1f b/s | %d corr", lastHorizontalBps, serverCorrections);
    }

    public enum BoostMode {
        MultiplyHorizontal,
        AddYawVelocity,
        PitchOptimizer
    }
}
