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
 * Drives Meteor's built-in {@link ElytraFly} in Pitch40 mode with auto-bound-adjust,
 * auto-firework, and 2b2t queue-exit re-enable.
 *
 * <p>Mirrors meteor-stashhunting-addon 1.21.1's {@code Pitch40Util} workflow: forces
 * Pitch40 mode while active, auto-resets the upper/lower bounds at the apex of each
 * climb, fires a rocket when vertical velocity drops below the threshold, and
 * re-enables ElytraFly when the player drops out of the 2b2t queue
 * ({@code allowFlying} goes back to {@code false}).
 */
public class ElytraAutoFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

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

    public ElytraAutoFly() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "elytra-auto-fly",
            "Auto-bound-adjust + auto-firework driver for Meteor's ElytraFly in Pitch40 mode. Mirrors Jeff's Pitch40Util.");
    }

    // Pattern from Pitch40Util: direct lookup + raw-cast settings.get("...").
    // Meteor registers ElytraFly before HunterBuddy modules load, so this is safe.
    private final Module elytraFly = Modules.get().get(ElytraFly.class);

    @SuppressWarnings({"unchecked", "rawtypes"})
    private final Setting<ElytraFlightModes> elytraFlyMode =
        (Setting<ElytraFlightModes>) elytraFly.settings.get("mode");

    private ElytraFlightModes oldValue;
    private int fireworkCooldown = 0;
    private boolean goingUp = true;
    private int elytraSwapSlot = -1;

    @Override
    public void onActivate() {
        oldValue = elytraFlyMode.get();
        // Force Meteor's ElytraFly into Pitch40 mode while this module is active.
        elytraFlyMode.set(ElytraFlightModes.Pitch40);
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

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (elytraFly.isActive()) {
            if (fireworkCooldown > 0) {
                fireworkCooldown--;
            }

            if (elytraSwapSlot != -1) {
                InvUtils.swap(elytraSwapSlot, true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                InvUtils.swapBack();
                elytraSwapSlot = -1;
            }

            // Fell below the lower bound: reset the bounds. This mainly fires when
            // the player isn't using fireworks and the climb stalls out.
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
        } else {
            // Wait for the player to drop out of the 2b2t queue (allowFlying -> false),
            // then re-enable ElytraFly and reset the bounds for a fresh climb.
            if (!mc.player.getAbilities().allowFlying) {
                elytraFly.toggle();
                resetBounds();
            }
        }
    }
}