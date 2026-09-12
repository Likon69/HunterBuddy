package com.hunterbuddy.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

public class AutoEatSync extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> hungerThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("hunger-threshold")
        .description("Eat when the hunger bar falls to or below this many half-drumsticks.")
        .defaultValue(17.5)
        .min(0.0)
        .sliderRange(0.0, 20.0)
        .build()
    );

    private final Setting<Double> healthThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("health-threshold")
        .description("Also eat when health falls to or below this many health points (2 points = 1 heart), and treats health at or below this as critical for the combat gate.")
        .defaultValue(6.0)
        .min(0.0)
        .sliderRange(0.0, 20.0)
        .build()
    );

    private final Setting<Double> emergencyHealth = sgGeneral.add(new DoubleSetting.Builder()
        .name("emergency-health")
        .description("At or below this many health points (2 points = 1 heart), eat no matter what: collisions, low flight speed and KillAuraPlus no longer hold back or cut the meal, and it retries right after a rocket takes the hand. Baritone is never paused; its rockets still take the hand.")
        .defaultValue(8.0)
        .min(0.0)
        .sliderRange(0.0, 20.0)
        .build()
    );

    private final Setting<Boolean> gapsOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("gaps-only")
        .description("Only eat golden apples and enchanted golden apples. Off, any food counts.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> combatLullOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("combat-eat")
        .description("While KillAuraPlus is active with a target, only start eating on a lull (no pressing target) or at critical health.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> eatInFlight = sgGeneral.add(new BoolSetting.Builder()
        .name("eat-in-flight")
        .description("Allow eating while gliding on an elytra. Off, eating only happens on the ground or during regear survival.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> flightMinSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("flight-min-speed")
        .description("Minimum horizontal speed, in blocks per tick, required to start eating while gliding.")
        .defaultValue(1.2)
        .min(0.0)
        .sliderRange(0.0, 3.0)
        .build()
    );

    private final Setting<Double> flightReleaseSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("flight-release-speed")
        .description("Horizontal speed, in blocks per tick, below which eating is released while gliding, such as a rocket kicking in.")
        .defaultValue(1.0)
        .min(0.0)
        .sliderRange(0.0, 3.0)
        .build()
    );

    private final Setting<Boolean> eatInRegearSurvival = sgGeneral.add(new BoolSetting.Builder()
        .name("eat-in-regear-survival")
        .description("Allow eating during AutoFlyingRegear when health drops to or below health-threshold. Off, AutoFlyingRegear always fully stands this module down.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> debug = sgGeneral.add(new BoolSetting.Builder()
        .name("debug")
        .description("Log start, finish and abort events for this module to chat.")
        .defaultValue(false)
        .build()
    );

    private boolean eating;
    private int eatSlot = -1;
    private int prevSlot = -1;
    private int eatTicks;
    private int tickCounter;
    private int lastWantEatLogTick = -1000;
    private boolean wasUsingItem;
    private boolean restartSent;
    private int handRetryAfterTick;
    private int noBiteRetryAfterTick;

    public AutoEatSync() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "auto-eat-sync", "Eats gap apples without ever pausing Baritone or the fly, dodging AutoFlyingRegear, rocket windows and KillAuraPlus swings.");
    }

    @Override
    public void onActivate() {
        eating = false;
        eatSlot = -1;
        prevSlot = -1;
        eatTicks = 0;
        tickCounter = 0;
        lastWantEatLogTick = -1000;
        handRetryAfterTick = 0;
        noBiteRetryAfterTick = 0;
    }

    @Override
    public void onDeactivate() {
        if (eating) stopEating("disabled");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tickCounter++;

        if (mc.player == null || mc.world == null || !mc.player.isAlive()) {
            if (eating) stopEating("disabled");
            return;
        }

        if (regearRunning()) {
            boolean healthCritical = (eatInRegearSurvival.get() && mc.player.getHealth() <= healthThreshold.get()) || emergency();
            if (!healthCritical) {
                if (eating) stopEating("regear");
                return;
            }
        }

        if (eating) {
            if (eatSlot != SlotUtils.OFFHAND && mc.player.getInventory().getSelectedSlot() != eatSlot) {
                prevSlot = -1;
                stopEating("hand-taken");
                handRetryAfterTick = tickCounter + 60;
                return;
            }

            if (!shouldEat()) {
                stopEating("satisfied");
                return;
            }

            eatTicks++;

            if (!wasUsingItem && eatTicks >= 20) {
                stopEating("no-bite");
                noBiteRetryAfterTick = tickCounter + 60;
                return;
            }

            if (mustStop()) {
                stopEating(mustStopReason());
                return;
            }

            if (!isEdible(stackAt(eatSlot))) {
                int newSlot = findSlot();
                if (newSlot == -1) {
                    stopEating("disabled");
                    return;
                }
                if (!changeSlot(newSlot)) {
                    stopEating("disabled");
                    return;
                }
            }

            continueEating();
            return;
        }

        boolean wantsToEat = shouldEat();
        if (wantsToEat && safeToStart() && tickCounter >= noBiteRetryAfterTick && (emergency() || tickCounter >= handRetryAfterTick)) {
            int slot = findSlot();
            if (slot != -1) beginEating(slot);
        } else if (wantsToEat && debug.get()) {
            logWantsToEat();
        }
    }

    public boolean isEating() {
        return eating;
    }

    private boolean regearRunning() {
        AutoFlyingRegear regear = Modules.get().get(AutoFlyingRegear.class);
        return regear != null && regear.isRunning();
    }

    private boolean shouldEat() {
        boolean hungerLow = mc.player.getHungerManager().getFoodLevel() <= hungerThreshold.get();
        boolean healthLow = mc.player.getHealth() <= healthThreshold.get() || emergency();
        if (!hungerLow && !healthLow) return false;

        return findSlot() != -1;
    }

    private boolean emergency() {
        return mc.player.getHealth() <= emergencyHealth.get();
    }

    private boolean mustStop() {
        if (emergency()) return false;
        return elytraMustStop() || combatMustStop();
    }

    private boolean elytraMustStop() {
        if (!mc.player.isGliding()) return false;
        if (!eatInFlight.get()) return true;

        Vec3d velocity = mc.player.getVelocity();
        double hSpeed = Math.hypot(velocity.x, velocity.z);
        return hSpeed < flightReleaseSpeed.get() || mc.player.horizontalCollision || mc.player.verticalCollision;
    }

    private boolean combatMustStop() {
        KillAuraPlus killAura = Modules.get().get(KillAuraPlus.class);
        return killAura != null && killAura.isAboutToAttack();
    }

    private String mustStopReason() {
        if (mc.player.isGliding()) {
            if (!eatInFlight.get()) return "disabled";

            Vec3d velocity = mc.player.getVelocity();
            double hSpeed = Math.hypot(velocity.x, velocity.z);
            if (hSpeed < flightReleaseSpeed.get()) return "rocket-window";
            if (mc.player.horizontalCollision || mc.player.verticalCollision) return "collision";
        }

        KillAuraPlus killAura = Modules.get().get(KillAuraPlus.class);
        if (killAura != null && killAura.isAboutToAttack()) return "target-in-range";

        return "disabled";
    }

    private String currentContext() {
        if (regearRunning()) return "regear-survival";
        if (mc.player.isGliding()) return "flight";

        KillAuraPlus killAura = Modules.get().get(KillAuraPlus.class);
        if (killAura != null && killAura.isActive() && killAura.hasActiveTarget()) return "combat";

        return "ground";
    }

    private void logWantsToEat() {
        if (tickCounter - lastWantEatLogTick < 40) return;
        lastWantEatLogTick = tickCounter;
        info("[AutoEatSync] wants to eat but waiting for a safe window");
    }

    private boolean safeToStart() {
        if (emergency()) return true;

        if (mc.player.isGliding()) {
            if (!eatInFlight.get()) return false;

            Vec3d velocity = mc.player.getVelocity();
            double hSpeed = Math.hypot(velocity.x, velocity.z);
            if (hSpeed < flightMinSpeed.get() || mc.player.horizontalCollision || mc.player.verticalCollision) return false;
        }

        KillAuraPlus killAura = Modules.get().get(KillAuraPlus.class);
        if (killAura != null && killAura.isActive()) {
            if (killAura.isAboutToAttack()) return false;

            if (combatLullOnly.get()) {
                boolean healthCritical = mc.player.getHealth() <= healthThreshold.get();
                if (killAura.hasActiveTarget() && !healthCritical) return false;
            }
        }

        return true;
    }

    private ItemStack stackAt(int slot) {
        if (slot == SlotUtils.OFFHAND) return mc.player.getOffHandStack();
        return mc.player.getInventory().getStack(slot);
    }

    private boolean isEdible(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        if (gapsOnly.get()) {
            Item item = stack.getItem();
            return item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE;
        }

        return stack.get(DataComponentTypes.FOOD) != null;
    }

    private int findSlot() {
        if (isEdible(mc.player.getOffHandStack())) return SlotUtils.OFFHAND;

        int slot = findInRange(SlotUtils.HOTBAR_START, SlotUtils.HOTBAR_END);
        if (slot != -1) return slot;

        return findInRange(SlotUtils.MAIN_START, SlotUtils.MAIN_END);
    }

    private int findInRange(int start, int end) {
        for (int i = start; i <= end; i++) {
            if (isEdible(mc.player.getInventory().getStack(i))) return i;
        }

        return -1;
    }

    private boolean changeSlot(int slot) {
        if (slot == SlotUtils.OFFHAND) {
            eatSlot = SlotUtils.OFFHAND;
            return true;
        }

        if (SlotUtils.isHotbar(slot)) {
            InvUtils.swap(slot, false);
            eatSlot = slot;
            return true;
        }

        int emptySlot = InvUtils.find(ItemStack::isEmpty, SlotUtils.HOTBAR_START, SlotUtils.HOTBAR_END).slot();
        if (emptySlot == -1) return false;

        InvUtils.move().from(slot).toHotbar(emptySlot);
        InvUtils.swap(emptySlot, false);
        eatSlot = emptySlot;
        return true;
    }

    private void beginEating(int slot) {
        prevSlot = mc.player.getInventory().getSelectedSlot();
        if (!changeSlot(slot)) return;

        mc.options.useKey.setPressed(true);
        if (!mc.player.isUsingItem()) Utils.rightClick();

        eating = true;
        eatTicks = 0;
        wasUsingItem = false;
        restartSent = false;

        if (debug.get()) {
            Vec3d velocity = mc.player.getVelocity();
            double hSpeed = Math.hypot(velocity.x, velocity.z);
            info("[AutoEatSync] eating " + stackAt(eatSlot).getItem().getName().getString() + " — context " + currentContext()
                + (emergency() ? ", emergency" : "")
                + ", hunger " + mc.player.getHungerManager().getFoodLevel() + ", health " + mc.player.getHealth()
                + ", speed " + String.format("%.2f", hSpeed) + " b/t");
        }
    }

    private void continueEating() {
        mc.options.useKey.setPressed(true);

        if (mc.player.isUsingItem()) {
            wasUsingItem = true;
            return;
        }

        if (wasUsingItem) {
            if (debug.get()) info("[AutoEatSync] finished after " + eatTicks + " ticks");
            stopEating("finished");
            return;
        }

        if (eatTicks > 10 && !restartSent) {
            Utils.rightClick();
            restartSent = true;
        }
    }

    private void stopEating(String reason) {
        if (debug.get()) info("[AutoEatSync] aborted after " + eatTicks + " ticks — reason " + reason);

        if (eatSlot != SlotUtils.OFFHAND && prevSlot != -1) changeSlot(prevSlot);

        mc.options.useKey.setPressed(false);
        eating = false;
        eatSlot = -1;
        prevSlot = -1;
        eatTicks = 0;
    }
}
