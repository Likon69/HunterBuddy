package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
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
 * {@link Pitch40Classic} (standalone, classic 0.5.8 behavior) instead of
 * Meteor's broken ElytraFly.
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

    // ---- Pitch40-style climb settings ----

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

    // ---- Runtime state ----

    private enum Phase { CLIMB, DESCEND }

    private final Pitch40Classic pitch40Classic = Modules.get().get(Pitch40Classic.class);

    private Phase currentPhase = Phase.CLIMB;
    private boolean pitch40WasActiveBefore = false;

    public ElytraAutoFly() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "elytra-auto-fly",
            "Custom elytra cycle: Pitch40Classic + auto-bound up to max-altitude, then manual-pitch descend to min-altitude, loop.");
    }

    @Override
    public void onActivate() {
        if (pitch40Classic == null) {
            error("Pitch40Classic module not found — cannot activate.");
            this.toggle();
            return;
        }
        pitch40WasActiveBefore = pitch40Classic.isActive();
        enterClimb();
    }

    @Override
    public void onDeactivate() {
        enterStandby();
    }

    private void resetBounds() {
        double y = mc.player.getY();
        pitch40Classic.lowerBound.set(y - 5);
        pitch40Classic.upperBound.set(y - 5);
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
        // Deactivate Pitch40Classic (which controls pitch itself)
        if (pitch40Classic.isActive()) {
            pitch40Classic.toggle();
        }
    }

    private void enterStandby() {
        // Restore Pitch40Classic to whatever state it was in before we activated.
        if (pitch40Classic != null && pitch40Classic.isActive() && !pitch40WasActiveBefore) {
            pitch40Classic.toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (currentPhase == Phase.CLIMB) {
            // Reconnect: if Pitch40Classic was turned off (2b2t queue exit, manual
            // toggle), re-enable when the player can fly again.
            if (!pitch40Classic.isActive()) {
                if (mc.player.getAbilities().allowFlying) {
                    enterClimb();
                }
                return;
            }
            tickClimb();
        } else {
            tickDescend();
        }
    }

    private void tickClimb() {
        // Cycle: switch to descend phase when max altitude reached.
        if (mc.player.getY() >= maxAltitude.get()) {
            enterDescend();
        }
    }

    private void tickDescend() {
        // Vanilla glide — set our descend pitch directly.
        mc.player.setPitch(descendPitch.get().floatValue());

        // Cycle: switch back to climb phase when min altitude reached.
        if (mc.player.getY() <= minAltitude.get()) {
            enterClimb();
        }
    }
}