package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.elytraboost.ElytraTakeoff;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Elytra flight recovery fallback. Monitors the player's gliding state and,
 * if the player stops flying or drops too low, attempts to re-deploy the
 * elytra (jump → fall → fly → firework boost).
 *
 * <p>Ported from mlep's {@code ElytraRecast}. The original depends on
 * mlep's {@code ElytraTakeoff} (helper), {@code Utils} (key-binding
 * helpers), and {@code Pitch40} (optional integration). This port inlines
 * the take-off helper and key-binding logic, and integrates with Meteor's
 * {@code Pitch40} module via string-based setting lookup (so the integration
 * works even if the class isn't directly importable).
 */
public class ElytraRecast extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgNether = settings.createGroup("Nether");
    private final SettingGroup sgOverworld = settings.createGroup("Overworld/End");

    private final Setting<Boolean> disableIfNoRockets = sgGeneral.add(new BoolSetting.Builder()
        .name("disable-if-no-rockets")
        .description("Automatically disable ElytraRecast when no firework rockets are available.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debugMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-messages")
        .description("Show debug messages in chat for state changes and recovery events.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> activationDelay = sgGeneral.add(new IntSetting.Builder()
        .name("activation-delay")
        .description("Ticks to wait between jump and elytra activation attempts.")
        .defaultValue(3)
        .min(1).max(20)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> rocketDelay = sgGeneral.add(new IntSetting.Builder()
        .name("rocket-delay")
        .description("Ticks between rocket usages during ascent.")
        .defaultValue(15)
        .min(5).max(60)
        .sliderRange(5, 60)
        .build()
    );

    private final Setting<Double> rotationSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("Degrees per tick to adjust pitch when ascending. Lower = smoother & more realistic.")
        .defaultValue(3.0)
        .min(0.5).max(15.0)
        .sliderRange(0.5, 15.0)
        .build()
    );

    private final Setting<Double> targetPitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-pitch")
        .description("Target pitch angle when ascending. Negative = looking up.")
        .defaultValue(-70.0)
        .min(-89.0).max(0.0)
        .sliderRange(-89.0, 0.0)
        .build()
    );

    private final Setting<Integer> netherFallDelay = sgNether.add(new IntSetting.Builder()
        .name("fall-delay")
        .description("Ticks to wait after stopping flight before triggering recovery in Nether.")
        .defaultValue(5)
        .min(1).max(40)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Boolean> usePitch40Bounds = sgOverworld.add(new BoolSetting.Builder()
        .name("use-pitch40-bounds")
        .description("Use Pitch40's lower bounds as the minimum altitude instead of the setting below.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> overworldTargetAltitude = sgOverworld.add(new IntSetting.Builder()
        .name("target-altitude")
        .description("Target Y level to ascend to in Overworld/End.")
        .defaultValue(360)
        .sliderRange(50, 400)
        .build()
    );

    private final Setting<Integer> overworldMinAltitude = sgOverworld.add(new IntSetting.Builder()
        .name("min-altitude")
        .description("Trigger recovery if Y drops below this in Overworld/End. Ignored if use-pitch40-bounds is enabled.")
        .defaultValue(310)
        .sliderRange(10, 400)
        .visible(() -> !usePitch40Bounds.get())
        .build()
    );

    private State state;
    private int tickCounter;
    private int rocketTickCounter;
    private boolean wasGliding;
    private boolean usedActivationRocket;
    private int notGlidingTicks;
    private boolean netherRocketUsed;
    private final ElytraTakeoff takeoff = new ElytraTakeoff();
    private int monitoringRecoveryCooldown = 0;
    private static final int MONITORING_RECOVERY_PAUSE = 80;

    public ElytraRecast() {
        super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "elytra-recast",
            "Flight recovery fallback. Monitors and recovers when you stop flying or drop too low.");
    }

    public boolean isAscending() {
        return state == State.ASCENDING;
    }

    public boolean isRecovering() {
        return takeoff.isActive() || state == State.ASCENDING;
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        if (!hasElytraEquipped()) {
            error("No elytra equipped!");
            toggle();
            return;
        }
        tickCounter = 0;
        rocketTickCounter = 0;
        wasGliding = mc.player.isFallFlying();
        usedActivationRocket = false;
        notGlidingTicks = 0;
        netherRocketUsed = false;
        takeoff.cancel();
        monitoringRecoveryCooldown = 0;
        state = State.MONITORING;
        if (debugMessages.get()) info("Monitoring flight...");
    }

    @Override
    public void onDeactivate() {
        takeoff.cancel();
        if (mc.options != null) mc.options.jumpKey.setPressed(false);
        state = null;
    }

    public void requestTakeoff() {
        if (isActive() && mc.player != null) {
            monitoringRecoveryCooldown = 0;
            beginRecovery("manual takeoff requested");
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || state == null || !hasElytraEquipped()) return;

        if (takeoff.isActive()) {
            takeoff.tickPre();
            return;
        }

        tickCounter++;
        rocketTickCounter++;
        if (state == State.MONITORING) handleMonitoring();
        else if (state == State.ASCENDING) handleAscending();
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (mc.player != null && mc.world != null && takeoff.isActive()) {
            takeoff.tickPost();
        }
    }

    private void handleMonitoring() {
        if (takeoff.isActive()) return;

        if (monitoringRecoveryCooldown > 0) {
            monitoringRecoveryCooldown--;
            wasGliding = mc.player.isFallFlying();
            return;
        }

        boolean currentlyGliding = mc.player.isFallFlying();
        if (isInNether()) {
            if (!currentlyGliding) {
                notGlidingTicks++;
                if (notGlidingTicks >= netherFallDelay.get()) {
                    if (debugMessages.get()) info("Nether: flight stopped for " + notGlidingTicks + " ticks! Recovering...");
                    triggerRecovery();
                    notGlidingTicks = 0;
                    return;
                }
            } else {
                notGlidingTicks = 0;
            }
        } else {
            double minAlt = getEffectiveMinAltitude();
            if (mc.player.getY() < minAlt) {
                if (!currentlyGliding) {
                    if (debugMessages.get()) info("Below min altitude (Y=" + (int) mc.player.getY() + " < " + (int) minAlt + ") and not flying! Recovering...");
                    triggerRecovery();
                    wasGliding = false;
                    return;
                }
                if (isPitch40ManagingFlight()) {
                    wasGliding = currentlyGliding;
                    return;
                }
                if (debugMessages.get()) info("Altitude too low (Y=" + (int) mc.player.getY() + " < " + (int) minAlt + ")! Ascending...");
                triggerRecovery();
                return;
            }
        }

        wasGliding = currentlyGliding;
    }

    /**
     * Effective minimum altitude. If {@code use-pitch40-bounds} is on and
     * Meteor's {@code Pitch40} module is active, read its {@code pitch40-lower-bounds}
     * setting (looked up by name to avoid a direct class dependency on
     * Meteor's internal Pitch40 class). Otherwise use the overworld setting.
     */
    @SuppressWarnings("unchecked")
    private double getEffectiveMinAltitude() {
        if (usePitch40Bounds.get()) {
            try {
                // Meteor's Modules.get(String) looks up by the registered module
                // short name, NOT the FQN. The actual module is "elytra-fly"; the
                // Pitch40 mode lives inside it. The "pitch40-lower-bounds" setting
                // is exposed on ElytraFly.
                Module elytraFly = Modules.get().get("elytra-fly");
                if (elytraFly != null && elytraFly.isActive()) {
                    Setting<Double> lower = (Setting<Double>) elytraFly.settings.get("pitch40-lower-bounds");
                    if (lower != null) return lower.get() - 10.0;
                }
            } catch (Throwable ignored) {}
        }
        return overworldMinAltitude.get();
    }

    private boolean isPitch40ManagingFlight() {
        try {
            Module elytraFly = Modules.get().get("elytra-fly");
            return elytraFly != null && elytraFly.isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void resetToMonitoring() {
        if (isActive()) {
            state = State.MONITORING;
            tickCounter = 0;
            rocketTickCounter = 0;
            usedActivationRocket = false;
            notGlidingTicks = 0;
            netherRocketUsed = false;
            takeoff.cancel();
            if (mc.player != null) wasGliding = mc.player.isFallFlying();
        }
    }

    private void triggerRecovery() {
        beginRecovery("recovery triggered");
    }

    private void beginRecovery(String reason) {
        if (!hasRockets()) {
            if (disableIfNoRockets.get()) {
                error("No firework rockets available! Disabling ElytraRecast.");
                toggle();
                return;
            }
            warning("No firework rockets available! Recovery may fail.");
        }

        if (debugMessages.get()) info(reason);

        tickCounter = 0;
        rocketTickCounter = rocketDelay.get();
        usedActivationRocket = false;
        if (mc.player.isFallFlying()) {
            enterAscending();
        } else {
            startTakeoff();
        }
    }

    private void startTakeoff() {
        takeoff.start(-1, new ElytraTakeoff.Listener() {
            @Override
            public void onTakeoffSuccess(double speedBps) {
                enterAscending();
                if (debugMessages.get()) info("Recovery takeoff complete at " + String.format("%.1f", speedBps) + " b/s");
            }

            @Override
            public void onTakeoffFailed(String reason) {
                state = State.MONITORING;
                monitoringRecoveryCooldown = MONITORING_RECOVERY_PAUSE;
                tickCounter = 0;
                if (debugMessages.get()) warning("Recovery takeoff failed: " + reason);
            }

            @Override
            public void onTakeoffDebug(String message) {
                if (debugMessages.get()) info(message);
            }
        });
    }

    private void enterAscending() {
        state = State.ASCENDING;
        rocketTickCounter = rocketDelay.get();
        tickCounter = 0;
        if (!useRocketWhileGliding() && disableIfNoRockets.get()) {
            error("No rockets for ascent! Disabling.");
            toggle();
        }
    }

    private void handleAscending() {
        if (!mc.player.isFallFlying()) {
            if (!hasRockets() && disableIfNoRockets.get()) {
                error("Lost flight and no rockets! Disabling.");
                toggle();
            } else {
                startTakeoff();
            }
            return;
        }

        if (isPitch40ManagingFlight()) {
            state = State.MONITORING;
            wasGliding = true;
            tickCounter = 0;
            return;
        }

        if (isInNether()) {
            adjustPitchUp();
            if (!netherRocketUsed) {
                if (useRocket()) {
                    netherRocketUsed = true;
                } else if (disableIfNoRockets.get()) {
                    error("No rockets for Nether recovery! Disabling.");
                    toggle();
                    return;
                }
            }
            if (tickCounter > 10) {
                if (debugMessages.get()) info("Nether recovery complete - baritone takes over.");
                state = State.MONITORING;
                wasGliding = true;
                tickCounter = 0;
                netherRocketUsed = false;
                return;
            }
        } else {
            int target = overworldTargetAltitude.get();
            if (mc.player.getY() >= target) {
                if (debugMessages.get()) info("Target altitude Y=" + target + " reached! Resuming monitoring.");
                state = State.MONITORING;
                wasGliding = true;
                tickCounter = 0;
                return;
            }

            adjustPitchUp();
            if (rocketTickCounter >= rocketDelay.get()) {
                if (useRocket()) {
                    rocketTickCounter = 0;
                } else if (disableIfNoRockets.get()) {
                    error("Out of rockets during ascent! Disabling.");
                    toggle();
                    return;
                }
            }
        }
    }

    private void adjustPitchUp() {
        float currentPitch = mc.player.getPitch();
        float target = targetPitch.get().floatValue();
        float speed = rotationSpeed.get().floatValue();
        float diff = target - currentPitch;
        if (Math.abs(diff) < 0.5f) return;
        float adjustment;
        if (Math.abs(diff) <= speed) adjustment = diff;
        else adjustment = diff > 0 ? speed : -speed;
        float newPitch = currentPitch + adjustment;
        newPitch = Math.max(-89.0f, Math.min(89.0f, newPitch));
        mc.player.setPitch(newPitch);
    }

    private boolean hasRockets() {
        return InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET}).found()
            || InvUtils.find(new Item[]{Items.FIREWORK_ROCKET}).found();
    }

    private boolean useRocket() {
        return useRocketWhileGliding();
    }

    /**
     * Use a firework rocket from hotbar (or inventory) while gliding.
     * Returns true if a firework was successfully used.
     */
    private boolean useRocketWhileGliding() {
        if (mc.player == null || mc.interactionManager == null || !mc.player.isFallFlying()) return false;

        FindItemResult hotbar = InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET});
        if (!hotbar.found()) {
            FindItemResult inv = InvUtils.find(new Item[]{Items.FIREWORK_ROCKET});
            if (!inv.found()) return false;
            int hotbarSlot = findEmptyHotbarSlot();
            if (hotbarSlot != -1) {
                InvUtils.move().from(inv.slot()).to(hotbarSlot);
                hotbar = InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET});
            }
        }
        if (!hotbar.found()) return false;

        if (hotbar.isOffhand()) {
            mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            mc.player.swingHand(Hand.OFF_HAND);
            return true;
        }
        if (mc.player.getMainHandStack().getItem() == Items.FIREWORK_ROCKET) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        // Firework is in hotbar but not in main hand (e.g. just moved from
        // inventory). Swap to it, use, swap back. Matches mlep ElytraRecast.java:432-445.
        if (!hotbar.isMainHand()) {
            InvUtils.swap(hotbar.slot(), true);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            mc.player.swingHand(Hand.MAIN_HAND);
            InvUtils.swapBack();
            return true;
        }
        return false;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private boolean hasElytraEquipped() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
    }

    private boolean isInNether() {
        return mc.world == null ? false : mc.world.getRegistryKey() == World.NETHER;
    }

    @Override
    public String getInfoString() {
        if (state == null || mc.player == null) return null;

        if (takeoff.isActive()) return "TAKEOFF " + takeoff.getPhase();

        double minAlt = isInNether() ? 0.0 : getEffectiveMinAltitude();
        if (state == State.MONITORING) {
            return isInNether()
                ? String.format("Y=%.0f", mc.player.getY())
                : String.format("Y=%.0f (min:%.0f)", mc.player.getY(), minAlt);
        }
        if (state == State.ASCENDING) {
            return isInNether()
                ? String.format("↑%.0f", mc.player.getY())
                : String.format("↑%.0f/%d", mc.player.getY(), overworldTargetAltitude.get());
        }
        return state.name();
    }

    private enum State { MONITORING, ASCENDING }
}