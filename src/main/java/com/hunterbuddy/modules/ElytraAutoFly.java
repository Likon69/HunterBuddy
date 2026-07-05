package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;

/**
 * Custom 2-phase elytra cycle driving our standalone {@link Pitch40Classic} module.
 *
 * <p>Does NOT use Meteor's ElytraFly or the old (removed) Pitch40 (which activates
 * Meteor's broken Pitch40 mode). Instead, uses {@link Pitch40Classic} which
 * controls pitch directly with the classic +40/-40 oscillation.
 *
 * <ol>
 *   <li><b>CLIMB</b>: Activate {@link Pitch40Classic}, set bounds.
 *       Player Y rises toward {@code max-altitude}.</li>
 *   <li><b>DESCEND</b>: Deactivate {@link Pitch40Classic}, set manual descend pitch.
 *       Player Y drops toward {@code min-altitude}.</li>
 *   <li>Loop.</li>
 * </ol>
 */
public class ElytraAutoFly extends Module {
    private final SettingGroup sgCycle = settings.getDefaultGroup();

    private final Setting<Integer> maxAltitude = sgCycle.add(new IntSetting.Builder()
        .name("max-altitude")
        .description("Switch from Pitch40Classic climb to manual-pitch descend when player Y reaches this. Rusherhack's 'Max Height'.")
        .defaultValue(10000)
        .min(100)
        .max(32000)
        .sliderRange(100, 32000)
        .build()
    );

    private final Setting<Integer> minAltitude = sgCycle.add(new IntSetting.Builder()
        .name("min-altitude")
        .description("Switch back to Pitch40Classic climb when player Y drops to this. Rusherhack's 'Min Height'.")
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

    private enum Phase { CLIMB, DESCEND }

    private final Pitch40Classic pitch40Classic = Modules.get().get(Pitch40Classic.class);

    private Phase currentPhase = Phase.CLIMB;
    private boolean pitch40WasActiveBefore = false;

    public ElytraAutoFly() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "elytra-auto-fly",
            "Custom elytra cycle using Pitch40Classic (standalone, not Meteor ElytraFly). Climb via Pitch40Classic, descend via manual pitch, loop.");
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
        if (pitch40Classic != null && pitch40Classic.isActive() && !pitch40WasActiveBefore) {
            pitch40Classic.toggle();
        }
    }

    private void enterClimb() {
        currentPhase = Phase.CLIMB;
        if (mc.player != null) {
            pitch40Classic.lowerBound.set(mc.player.getY() - 5);
            pitch40Classic.upperBound.set(mc.player.getY() - 5);
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
        if (mc.player.getY() >= maxAltitude.get()) {
            enterDescend();
        }
    }

    private void tickDescend() {
        mc.player.setPitch(descendPitch.get().floatValue());
        if (mc.player.getY() <= minAltitude.get()) {
            enterClimb();
        }
    }
}