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
 * Custom 2-phase elytra cycle driving our {@link Pitch40} module.
 *
 * <p>Pattern based on meteor-stashhunting-addon's {@code Pitch40Util} for the
 * climb phase, extended with a custom cycle on top:
 * <ol>
 *   <li><b>CLIMB</b>: {@link Pitch40} activates Meteor's ElytraFly in Pitch40
 *       mode, syncs bounds from this module's settings, handles auto-firework.
 *       Player Y rises toward {@code max-altitude}.</li>
 *   <li><b>DESCEND</b>: {@link Pitch40} is deactivated (Meteor's ElytraFly
 *       is turned off), player glides with manual pitch ({@code descend-pitch}).
 *       Player Y drops toward {@code min-altitude}.</li>
 *   <li>Loop back to CLIMB with fresh bounds reset.</li>
 * </ol>
 *
 * <p>Uses our own {@link Pitch40} module (not the deleted {@code Pitch40Classic}).
 * {@link Pitch40} owns Meteor ElytraFly activation and bound-syncing.
 */
public class ElytraAutoFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCycle = settings.createGroup("Cycle");

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

    private final Pitch40 pitch40 = Modules.get().get(Pitch40.class);

    private Phase currentPhase = Phase.CLIMB;
    private boolean pitch40WasActiveBefore = false;

    public ElytraAutoFly() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "elytra-auto-fly",
            "Custom elytra cycle driving our Pitch40 module. Climb via Pitch40 (auto-bound + auto-firework), descend via manual pitch, loop.");
    }

    @Override
    public void onActivate() {
        if (pitch40 == null) {
            error("Pitch40 module not found — cannot activate.");
            this.toggle();
            return;
        }
        pitch40WasActiveBefore = pitch40.isActive();
        enterClimb();
    }

    @Override
    public void onDeactivate() {
        enterStandby();
    }

    private void resetBounds() {
        double y = mc.player.getY();
        pitch40.pitch40LowerBounds.set(y - 5);
        pitch40.pitch40UpperBounds.set(y - 5);
    }

    private void enterClimb() {
        currentPhase = Phase.CLIMB;
        if (mc.player != null) {
            resetBounds();
        }
        if (!pitch40.isActive()) {
            pitch40.toggle();
        }
    }

    private void enterDescend() {
        currentPhase = Phase.DESCEND;
        // Deactivate Pitch40 (which also deactivates Meteor's ElytraFly)
        if (pitch40.isActive()) {
            pitch40.toggle();
        }
    }

    private void enterStandby() {
        // Restore Pitch40 to whatever state it was in before we activated.
        if (pitch40 != null && pitch40.isActive() && !pitch40WasActiveBefore) {
            pitch40.toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (currentPhase == Phase.CLIMB) {
            // Reconnect: if Pitch40 was turned off (2b2t queue exit, manual
            // toggle), re-enable when the player can fly again.
            if (!pitch40.isActive()) {
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