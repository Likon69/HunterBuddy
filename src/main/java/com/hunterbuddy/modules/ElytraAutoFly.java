package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;

/**
 * Custom 2-phase elytra cycle driving our standalone {@link Pitch40Classic} module.
 *
 * <p>Original interface preserved (same settings as before) but uses our
 * {@link Pitch40Classic} (standalone, classic +40/-40 behavior) instead of
 * Meteor's ElytraFly.
 *
 * <ol>
 *   <li><b>CLIMB</b>: Activate {@link Pitch40Classic}, set bounds.
 *       Player Y rises toward {@code max-altitude}.</li>
 *   <li><b>DESCEND</b>: Deactivate {@link Pitch40Classic}, set manual pitch
 *       ({@code descend-pitch}). Player Y drops toward {@code min-altitude}.</li>
 *   <li>Loop back to CLIMB with fresh bounds reset.</li>
 * </ol>
 */
public class ElytraAutoFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCycle = settings.createGroup("Cycle");

    // ---- Pitch40Util-style climb settings ----

    public final Setting<Boolean> autoBoundAdjust = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-adjust-bounds")
        .description("Adjusts your bounds to make you continue to gain height. Good for fixing falling on reconnect or lag, etc.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> boundGap = sgGeneral.add(new DoubleSetting.Builder()
        .name("bound-gap")
        .description("The gap between the upper and lower bounds. Used when reconnecting, or when at max height if Auto Adjust Bounds is enabled.")
        .defaultValue(60.0)
        .sliderRange(50.0, 100.0)
        .build()
    );

    // ---- Cycle settings (descend phase) ----

    private final Setting<Integer> maxAltitude = sgCycle.add(new IntSetting.Builder()
        .name("max-altitude")
        .description("Switch from Pitch40 climb to manual-pitch descend when player Y reaches this. Rusherhack's 'Max Height'.")
        .defaultValue(10000)
        .min(100)
        .max(32000)
        .sliderRange(100, 32000)
        .build()
    );

    private final Setting<Integer> minAltitude = sgCycle.add(new IntSetting.Builder()
        .name("min-altitude")
        .description("Switch back to Pitch40 climb when player Y drops to this. Rusherhack's 'Min Height'.")
        .defaultValue(256)
        .min(-64)
        .max(10000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Integer> descendPitch = sgCycle.add(new IntSetting.Builder()
        .name("descend-pitch")
        .description("Pitch angle during the descend phase. Higher = faster drop. Rusherhack's 'Down Pitch'.")
        .defaultValue(30)
        .min(5)
        .max(80)
        .sliderRange(5, 80)
        .build()
    );

    private final Setting<Boolean> descendAutoFirework = sgCycle.add(new BoolSetting.Builder()
        .name("descend-auto-firework")
        .description("Fire fireworks automatically during the DESCEND phase to maintain speed, like the CLIMB phase does via Pitch40Classic.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> descendFireworkCooldownTicks = sgCycle.add(new IntSetting.Builder()
        .name("descend-firework-cooldown")
        .description("Ticks between automatic firework fires during the DESCEND phase.")
        .defaultValue(20)
        .min(1)
        .max(200)
        .sliderRange(5, 60)
        .visible(descendAutoFirework::get)
        .build()
    );

    // ---- Runtime state ----

    private enum Phase { CLIMB, DESCEND }

    private final Pitch40Classic pitch40Classic = Modules.get().get(Pitch40Classic.class);

    private Phase currentPhase = Phase.CLIMB;
    private boolean pitch40WasActiveBefore = false;
    private boolean goingUp = true;
    private int descendFireworkCooldown;

    public ElytraAutoFly() {
        super(HunterBuddyAddon.HUNT_CATEGORY, "elytra-auto-fly",
            "Custom elytra cycle driving Pitch40Classic: climb (Pitch40Classic) + auto-bound, then manual-pitch descend, loop.");
    }

    @Override
    public void onActivate() {
        if (pitch40Classic == null) {
            error("Pitch40Classic module not found — cannot activate.");
            this.toggle();
            return;
        }
        pitch40WasActiveBefore = pitch40Classic.isActive();
        descendFireworkCooldown = 0;
        enterClimb();
    }

    @Override
    public void onDeactivate() {
        if (pitch40Classic != null && pitch40Classic.isActive()) {
            pitch40Classic.toggle();
        }
    }

    private void resetBounds() {
        if (mc.player == null) return;
        double y = mc.player.getY();
        pitch40Classic.upperBound.set(y);
        pitch40Classic.lowerBound.set(y - boundGap.get());
    }

    private void enterClimb() {
        currentPhase = Phase.CLIMB;
        if (mc.player != null) {
            resetBounds();
        }
        if (!pitch40Classic.isActive()) {
            pitch40Classic.toggle();
        }
    }

    private void enterDescend() {
        currentPhase = Phase.DESCEND;
        if (pitch40Classic.isActive()) {
            pitch40Classic.toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (currentPhase == Phase.CLIMB) {
            // Respect user choice: if Pitch40Classic is disabled, do nothing.
            // Don't auto re-activate every tick (that was overzealous).
            // User can re-enable Pitch40Classic manually to resume the climb cycle.
            if (pitch40Classic.isActive()) {
                tickClimb();
            }
        } else {
            tickDescend();
        }
    }

    private void tickClimb() {
        double y = mc.player.getY();
        double velocityY = mc.player.getVelocity().y;

        // JEFF Pitch40Util logic (exact copy):
        // 1. Player fell below lower bound - 10: reset bounds, skip rest
        if (autoBoundAdjust.get() && y <= pitch40Classic.lowerBound.get() - 10) {
            resetBounds();
            return;
        }

        // 2. upClamp pitch = facing up (goingUp phase). Mode-dependent:
        //    -40.0 in CLASSIC, -54.77 in EFFICIENT (drift). Read via getter
        //    to stay in sync with Pitch40Classic mode changes.
        if (mc.player.getPitch() == pitch40Classic.getUpClamp()) {
            goingUp = true;
        }
        // 3. Apex: going up but vertical velocity <= 0 -> grab new bounds
        else if (autoBoundAdjust.get() && goingUp && velocityY <= 0) {
            goingUp = false;
            resetBounds();
        }

        // Cycle: switch to descend phase when max altitude reached.
        if (y >= maxAltitude.get()) {
            enterDescend();
        }
    }

    private void tickDescend() {
        // Auto-firework to maintain speed during descent (like CLIMB does via Pitch40Classic).
        if (descendAutoFirework.get()) {
            if (descendFireworkCooldown > 0) {
                descendFireworkCooldown--;
            } else {
                Utils.firework(mc, false);
                descendFireworkCooldown = descendFireworkCooldownTicks.get();
            }
        }

        // Vanilla glide — set our descend pitch directly.
        mc.player.setPitch(descendPitch.get().floatValue());

        // Cycle: switch back to climb phase when min altitude reached.
        if (mc.player.getY() <= minAltitude.get()) {
            enterClimb();
        }
    }
}