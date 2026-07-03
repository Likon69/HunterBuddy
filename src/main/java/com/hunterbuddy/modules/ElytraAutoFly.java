package com.hunterbuddy.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-cycle elytra fly that drives Meteor's built-in {@link ElytraFly}.
 *
 * <p>Uses Meteor's {@link ElytraFlightModes#Pitch40} for climbing and
 * {@link ElytraFlightModes#Vanilla} for descending (Vanilla is the only mode
 * that lets the player control pitch directly). Reuses the auto-bound-adjust
 * and firework logic from meteor-stashhunting-addon's Pitch40Util.
 */
public class ElytraAutoFly extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> highAltitude = sgGeneral.add(new IntSetting.Builder()
        .name("high-altitude")
        .description("Climb target. When player Y reaches this, switch to descent.")
        .defaultValue(10000)
        .min(100)
        .max(32000)
        .sliderRange(100, 32000)
        .build()
    );

    private final Setting<Integer> lowAltitude = sgGeneral.add(new IntSetting.Builder()
        .name("low-altitude")
        .description("Descent target. When player Y drops to this, switch back to climb.")
        .defaultValue(256)
        .min(-64)
        .max(10000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Integer> downPitch = sgGeneral.add(new IntSetting.Builder()
        .name("down-pitch")
        .description("Pitch angle during descent. Higher = faster drop. 30 is a sensible default.")
        .defaultValue(30)
        .min(5)
        .max(80)
        .sliderRange(5, 80)
        .build()
    );

    private final Setting<Double> boundGap = sgGeneral.add(new DoubleSetting.Builder()
        .name("bound-gap")
        .description("Gap between upper and lower bounds inside Meteor's Pitch40 mode.")
        .defaultValue(60.0)
        .min(10.0)
        .max(200.0)
        .sliderRange(10.0, 200.0)
        .build()
    );

    private final Setting<Boolean> autoFirework = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-firework")
        .description("Auto-use a firework during climb when vertical velocity is low.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> velocityThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("velocity-threshold")
        .description("Use a firework when upward velocity drops below this value.")
        .defaultValue(-0.05)
        .min(-0.5)
        .max(1.0)
        .sliderRange(-0.5, 1.0)
        .visible(autoFirework::get)
        .build()
    );

    private final Setting<Integer> fireworkCooldownTicks = sgGeneral.add(new IntSetting.Builder()
        .name("firework-cooldown-ticks")
        .description("Cooldown after using a firework, in ticks.")
        .defaultValue(10)
        .min(0)
        .max(100)
        .sliderRange(0, 100)
        .visible(autoFirework::get)
        .build()
    );

    private final Setting<Boolean> autoToggleElytraFly = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-toggle-elytrafly")
        .description("Automatically enable Meteor's ElytraFly when this module turns on, disable when it turns off.")
        .defaultValue(true)
        .build()
    );

    // ---- Runtime state ----

    private final List<ElytraFlyStage> stages = new ArrayList<>();
    private int currentIndex = 0;

    private ElytraFly meteorElytraFly;
    private ElytraFlightModes savedMode;
    private int fireworkCooldown = 0;

    public ElytraAutoFly(Category category) {
        super(category, "elytra-auto-fly",
            "Auto elytra fly cycle. Climbs via Meteor's Pitch40 + auto-bound-adjust; descends via Vanilla + manual pitch; repeats.");

        stages.add(new ClimbStage());
        stages.add(new DescendStage());
        resetStages();
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        meteorElytraFly = Modules.get().get(ElytraFly.class);
        if (meteorElytraFly == null) {
            error("Meteor's ElytraFly module not found.");
            toggle();
            return;
        }

        savedMode = meteorElytraFly.flightMode.get();

        if (autoToggleElytraFly.get() && !meteorElytraFly.isActive()) {
            meteorElytraFly.toggle();
        }

        meteorElytraFly.flightMode.set(ElytraFlightModes.Pitch40);
        resetStages();
    }

    @Override
    public void onDeactivate() {
        if (meteorElytraFly == null) return;
        meteorElytraFly.flightMode.set(savedMode);
        if (autoToggleElytraFly.get() && meteorElytraFly.isActive()) {
            meteorElytraFly.toggle();
        }
    }

    @EventHandler
    public void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (meteorElytraFly == null) return;

        // Reconnect handling: if ElytraFly is off but the player can fly again,
        // re-enable it (like Pitch40Util does).
        if (autoToggleElytraFly.get() && !meteorElytraFly.isActive() && mc.player.getAbilities().allowFlying) {
            meteorElytraFly.toggle();
            resetStages();
            return;
        }

        if (!meteorElytraFly.isActive()) return;
        if (!mc.player.isGliding()) return;

        if (fireworkCooldown > 0) fireworkCooldown--;

        try {
            if (stages.get(currentIndex).work()) {
                stages.get(currentIndex).reset();
                currentIndex = (currentIndex + 1) % stages.size();
            }
        } catch (Throwable t) {
            error("Stage error: " + t.getMessage());
            t.printStackTrace();
            toggle();
        }
    }

    private void resetStages() {
        for (ElytraFlyStage stage : stages) stage.reset();
        currentIndex = 0;
        fireworkCooldown = 0;
    }

    // ---- Stage helpers (package-internal access for stage classes) ----

    ElytraFly meteorElytraFly() { return meteorElytraFly; }
    int fireworkCooldown() { return fireworkCooldown; }
    void setFireworkCooldown(int v) { this.fireworkCooldown = v; }

    Setting<Double> pitch40UpperBounds() { return meteorElytraFly.pitch40upperBounds; }
    Setting<Double> pitch40LowerBounds() { return meteorElytraFly.pitch40lowerBounds; }

    // ---- Stage pattern (per agent.md Hard Rule #6) ----

    private abstract class ElytraFlyStage {
        public abstract boolean work();
        public abstract void reset();
        public String toShortString() { return getClass().getSimpleName(); }
    }

    /** Climb phase: drive Meteor's ElytraFly in Pitch40 + auto-bound-adjust + auto-firework. */
    private class ClimbStage extends ElytraFlyStage {
        private boolean goingUp = true;

        @Override
        public boolean work() {
            meteorElytraFly.flightMode.set(ElytraFlightModes.Pitch40);

            // -40 pitch = looking up (Meteor's Pitch40 mode)
            if (mc.player.getPitch() == -40) {
                goingUp = true;

                if (autoFirework.get()
                    && mc.player.getVelocity().y < velocityThreshold.get()
                    && mc.player.getY() < pitch40UpperBounds().get()) {
                    if (fireworkCooldown == 0) {
                        FindItemResult result = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
                        if (result.found() && result.isHotbar()) {
                            InvUtils.swap(result.slot(), true);
                            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                            InvUtils.swapBack();
                            setFireworkCooldown(fireworkCooldownTicks.get());
                        }
                    }
                }
            }
            // At apex (vertical velocity <= 0 while still going up) -> reset bounds
            else if (goingUp && mc.player.getVelocity().y <= 0) {
                goingUp = false;
                pitch40UpperBounds().set(mc.player.getY() - 5);
                pitch40LowerBounds().set(mc.player.getY() - 5 - boundGap.get());
            }

            // Stay in climb until we reach high altitude.
            return mc.player.getY() >= highAltitude.get();
        }

        @Override
        public void reset() {
            goingUp = true;
        }
    }

    /** Descend phase: switch to Vanilla mode + manually pitch the player down. */
    private class DescendStage extends ElytraFlyStage {
        @Override
        public boolean work() {
            meteorElytraFly.flightMode.set(ElytraFlightModes.Vanilla);
            mc.player.setPitch(downPitch.get().floatValue());
            return mc.player.getY() <= lowAltitude.get();
        }

        @Override
        public void reset() {
            // No state.
        }
    }
}
