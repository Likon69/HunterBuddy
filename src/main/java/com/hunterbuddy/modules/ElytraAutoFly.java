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
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Custom 2-phase elytra cycle driving Meteor's built-in {@link ElytraFly}.
 *
 * <p>Pattern based on meteor-stashhunting-addon's {@code Pitch40Util} for the
 * climb phase, extended with a custom cycle on top:
 * <ol>
 *   <li><b>CLIMB</b>: Meteor's {@link ElytraFlightModes#Pitch40} + auto-bound-adjust +
 *       auto-firework. Player Y rises toward {@code max-altitude}.</li>
 *   <li><b>DESCEND</b>: Meteor's {@link ElytraFlightModes#Vanilla} + manual pitch
 *       ({@code descend-pitch}). Player Y drops toward {@code min-altitude}.</li>
 *   <li>Loop back to CLIMB with fresh bounds reset.</li>
 * </ol>
 *
 * <p>Settings mirror Rusherhack's EflyModule ({@code MaxHeight}, {@code MinHeight},
 * {@code DownPitch}) plus Jeff's Pitch40Util ({@code auto-adjust-bounds},
 * {@code bound-gap}, {@code auto-firework}, {@code velocity-threshold},
 * {@code cooldown-ticks}).
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

    public final Setting<Boolean> autoFirework = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-firework")
        .description("Uses a firework automatically if your velocity is too low.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Double> velocityThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("velocity-threshold")
        .description("Velocity must be below this value when going up for firework to activate.")
        .defaultValue(-0.05)
        .sliderRange(-0.5, 1.0)
        .visible(autoFirework::get)
        .build()
    );

    public final Setting<Integer> fireworkCooldownTicks = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-ticks")
        .description("Cooldown after using a firework in ticks.")
        .defaultValue(10)
        .sliderRange(0, 100)
        .visible(autoFirework::get)
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

    private final Module elytraFly = Modules.get().get(ElytraFly.class);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private final Setting<ElytraFlightModes> elytraFlyMode =
        (Setting<ElytraFlightModes>) elytraFly.settings.get("mode");

    private ElytraFlightModes oldValue;
    private int fireworkCooldown = 0;
    private boolean goingUp = true;
    private int elytraSwapSlot = -1;
    private Phase currentPhase = Phase.CLIMB;

    public ElytraAutoFly() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "elytra-auto-fly",
            "Custom elytra cycle: Pitch40 + auto-bound + auto-firework up to max-altitude, then manual-pitch descend to min-altitude, loop.");
    }

    @Override
    public void onActivate() {
        oldValue = elytraFlyMode.get();
        elytraFlyMode.set(ElytraFlightModes.Pitch40);
        currentPhase = Phase.CLIMB;
        goingUp = true;
        fireworkCooldown = 0;
        elytraSwapSlot = -1;
    }

    @Override
    public void onDeactivate() {
        if (elytraFly.isActive()) {
            elytraFly.toggle();
        }
        elytraFlyMode.set(oldValue);
    }

    private void resetBounds() {
        Setting<Double> upperBounds = (Setting<Double>) elytraFly.settings.get("pitch40-upper-bounds");
        upperBounds.set(mc.player.getY() - 5);
        Setting<Double> lowerBounds = (Setting<Double>) elytraFly.settings.get("pitch40-lower-bounds");
        lowerBounds.set(mc.player.getY() - 5 - boundGap.get());
    }

    private void enterDescend() {
        currentPhase = Phase.DESCEND;
        // Deactivate Meteor's ElytraFly entirely so the player glides with
        // pure vanilla physics at the manual descend pitch. The mode setting
        // (Pitch40) is preserved, so re-activation on enterClimb() resumes
        // straight back into the climb.
        if (elytraFly.isActive()) {
            elytraFly.toggle();
        }
    }

    private void enterClimb() {
        currentPhase = Phase.CLIMB;
        // Ensure Pitch40 mode (in case the user changed it via the GUI), then
        // re-enable ElytraFly for a fresh climb.
        elytraFlyMode.set(ElytraFlightModes.Pitch40);
        if (!elytraFly.isActive()) {
            elytraFly.toggle();
        }
        goingUp = true;
        resetBounds();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Dispatch on currentPhase FIRST, not on elytraFly.isActive(). During
        // DESCEND ElytraFly is intentionally OFF (player glides pure-vanilla at
        // the manual pitch), so checking isActive() here would fall into the
        // reconnect branch and re-enable ElytraFly -> oscillation spam at the
        // boundary altitude.
        if (currentPhase == Phase.CLIMB) {
            // Reconnect: if ElytraFly was turned off (2b2t queue exit, manual
            // toggle), re-enable when the player can fly again.
            if (!elytraFly.isActive()) {
                if (!mc.player.getAbilities().allowFlying) {
                    elytraFly.toggle();
                    enterClimb();
                }
                return;
            }
            tickClimb();
        } else {
            // DESCEND phase: ElytraFly is intentionally OFF. Don't touch it.
            tickDescend();
        }
    }

    private void tickClimb() {
        if (fireworkCooldown > 0) {
            fireworkCooldown--;
        }

        if (elytraSwapSlot != -1) {
            InvUtils.swap(elytraSwapSlot, true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InvUtils.swapBack();
            elytraSwapSlot = -1;
        }

        // Fell below the lower bound: reset. Mainly fires when the player isn't
        // using fireworks and the climb stalls out.
        if (autoBoundAdjust.get()
            && mc.player.getY() <= (double) elytraFly.settings.get("pitch40-lower-bounds").get() - 10) {
            resetBounds();
            return;
        }

        // -40 pitch = facing up (Pitch40 mode convention).
        if (mc.player.getPitch() == -40) {
            goingUp = true;
            if (autoFirework.get()
                && mc.player.getVelocity().y < velocityThreshold.get()
                && mc.player.getY() < (double) elytraFly.settings.get("pitch40-upper-bounds").get()) {
                if (fireworkCooldown == 0) {
                    FindItemResult result = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
                    if (result.found() && result.isHotbar()) {
                        InvUtils.swap(result.slot(), true);
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        InvUtils.swapBack();
                        fireworkCooldown = fireworkCooldownTicks.get();
                    }
                }
            }
        }
        // Apex: vertical velocity dropped to <= 0 while still climbing.
        // Set the new upper/lower bounds based on current Y.
        else if (autoBoundAdjust.get() && goingUp && mc.player.getVelocity().y <= 0) {
            goingUp = false;
            resetBounds();
        }

        // Cycle: switch to descend phase when max altitude reached.
        if (mc.player.getY() >= maxAltitude.get()) {
            enterDescend();
        }
    }

    private void tickDescend() {
        // Vanilla mode lets the player control pitch directly — set our descend pitch.
        mc.player.setPitch(descendPitch.get().floatValue());

        // Cycle: switch back to climb phase when min altitude reached.
        if (mc.player.getY() <= minAltitude.get()) {
            enterClimb();
        }
    }
}