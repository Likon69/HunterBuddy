/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class AutoEXPPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgElytra = settings.createGroup("Elytra Rotation");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Which items to repair.")
        .defaultValue(Mode.Both)
        .build()
    );

    private final Setting<Boolean> replenish = sgGeneral.add(new BoolSetting.Builder()
        .name("replenish")
        .description("Automatically replenishes exp into a selected hotbar slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> slot = sgGeneral.add(new IntSetting.Builder()
        .name("exp-slot")
        .description("The slot to replenish exp into.")
        .visible(replenish::get)
        .defaultValue(6)
        .range(1, 9)
        .sliderRange(1, 9)
        .build()
    );

    private final Setting<Integer> minThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("min-threshold")
        .description("The minimum durability percentage that an item needs to fall to, to be repaired.")
        .defaultValue(30)
        .range(1, 100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Integer> maxThreshold = sgGeneral.add(new IntSetting.Builder()
        .name("max-threshold")
        .description("The maximum durability percentage to repair items to.")
        .defaultValue(80)
        .range(1, 100)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> slowFinish = sgGeneral.add(new BoolSetting.Builder()
        .name("slow-finish")
        .description("Full speed up to 75 percent, then one bottle a second the rest of the way, until the item is full or the elytra goes off to be swapped. The last stretch is where the waste is -- a bottle every tick there buys almost nothing, because the orbs from the ones already thrown are still on their way in.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-elytra")
        .description("Ignore elytra when repairing. Has no effect while auto-swap-elytra is on.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> turnOffWhenDone = sgGeneral.add(new BoolSetting.Builder()
        .name("turn-off-when-done")
        .description("Turn the module off once nothing is left to repair: no target is chosen, and with the rotation on, no elytra the rotation would still pick up waits in the inventory. An elytra above swap-below is left alone by the rotation, so it does not hold the module open either.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> autoSwapElytra = sgElytra.add(new BoolSetting.Builder()
        .name("auto-swap-elytra")
        .description("Once the worn elytra is repaired, store it and wear the most damaged elytra from your inventory, so a whole stock gets repaired one after another. Needs mending on both and a mode that covers armor.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> swapAt = sgElytra.add(new IntSetting.Builder()
        .name("swap-at")
        .description("Durability percentage the worn elytra has to reach before it is stored away.")
        .defaultValue(98)
        .range(50, 100)
        .sliderRange(50, 100)
        .visible(autoSwapElytra::get)
        .build()
    );

    private final Setting<Integer> swapBelow = sgElytra.add(new IntSetting.Builder()
        .name("swap-below")
        .description("Only wear an elytra from your inventory if it sits at or under this percentage. The most damaged one is always picked first.")
        .defaultValue(50)
        .range(1, 99)
        .sliderRange(1, 99)
        .visible(autoSwapElytra::get)
        .build()
    );

    private final Setting<Integer> swapDelay = sgElytra.add(new IntSetting.Builder()
        .name("swap-delay")
        .description("Ticks to wait after a swap before touching the inventory again, so the server has time to answer.")
        .defaultValue(10)
        .range(1, 100)
        .sliderRange(1, 100)
        .visible(autoSwapElytra::get)
        .build()
    );

    private final Setting<Boolean> notifySwap = sgElytra.add(new BoolSetting.Builder()
        .name("notify-swap")
        .description("Print a chat line on every elytra swap.")
        .defaultValue(true)
        .visible(autoSwapElytra::get)
        .build()
    );

    private static final int CHEST_INDEX = SlotUtils.ARMOR_START + EquipmentSlot.CHEST.getEntitySlotId();

    private int repairingI;
    private int swapTimer;

    /** Consecutive ticks with nothing to do, for turn-off-when-done. */
    private int idleTicks;

    /**
     * Three seconds of confirmed idleness before the module lets go. A single
     * quiet tick is not "done" -- it is also what the middle of a swap looks
     * like, one elytra stored and the next not yet chosen.
     */
    private static final int DONE_TICKS = 60;

    /** Ticks left before the next bottle while the slow finish is holding it back. */
    private int slowTimer;

    /** Durability at which slow-finish stops throwing every tick, in percent. */
    private static final double SLOW_ABOVE = 75.0;

    /** One bottle a second once past that. */
    private static final int SLOW_INTERVAL = 20;

    public AutoEXPPlus() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "auto-exp-plus", "Automatically repairs your armor and tools in pvp.");
    }

    @Override
    public void onActivate() {
        repairingI = -1;
        swapTimer = 0;
        slowTimer = 0;
        idleTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (swapTimer > 0) swapTimer--;
        else if (elytraRotationActive() && rotateElytra()) return;

        if (repairingI == -1) {
            if (mode.get() != Mode.Hands) {
                for (EquipmentSlot slot : AttributeModifierSlot.ARMOR) {
                    ItemStack stack = mc.player.getEquippedStack(slot);
                    if (elytraRotationActive() && stack.getItem() == Items.ELYTRA) {
                        // The rotation repairs up to swap-at instead of max-threshold, otherwise a
                        // freshly worn broken elytra would stop short and never be stored away.
                        if (elytraWantsRepair(stack)) {
                            repairingI = CHEST_INDEX;
                            break;
                        }
                        continue;
                    }
                    if (ignoreElytra.get() && stack.getItem() == net.minecraft.item.Items.ELYTRA) continue;
                    if (needsRepair(stack, minThreshold.get())) {
                        repairingI = SlotUtils.ARMOR_START + slot.getEntitySlotId();
                        break;
                    }
                }
            }

            if (mode.get() != Mode.Armor && repairingI == -1) {
                for (Hand hand : Hand.values()) {
                    if (needsRepair(mc.player.getStackInHand(hand), minThreshold.get())) {
                        repairingI = hand == Hand.MAIN_HAND ? mc.player.getInventory().getSelectedSlot() : SlotUtils.OFFHAND;
                        break;
                    }
                }
            }
        }

        // Done means the module would do nothing from here on: no target chosen,
        // and with the rotation running, no damaged elytra left in the inventory
        // to be worn next. A pending swap is not done -- its candidate is still
        // counted -- and a damaged worn elytra picks itself as target above.
        if (turnOffWhenDone.get()) {
            boolean done = repairingI == -1
                && (!elytraRotationActive() || countRotationCandidates() == 0);

            if (!done) {
                idleTicks = 0;
            }
            else if (++idleTicks >= DONE_TICKS) {
                info("Nothing left to repair, turning off.");
                toggle();
                return;
            }
        }

        if (repairingI != -1) {
            ItemStack repairing = mc.player.getInventory().getStack(repairingI);
            double target;

            if (elytraRotationActive() && repairingI == CHEST_INDEX && repairing.getItem() == Items.ELYTRA) {
                if (!elytraWantsRepair(repairing)) {
                    repairingI = -1;
                    return;
                }
                // The rotation fills to swap-at, so that is the line the throwing has
                // to stop at too: measured against max-threshold it would call the
                // elytra finished while the swap is still waiting for the rest.
                target = swapAt.get();
            }
            else if (slowFinish.get()) {
                // Full, not max-threshold: the point of easing off at the end is to
                // reach the end. Tested on the damage itself rather than through
                // needsRepair, which reads "at or below the threshold" and would call
                // an undamaged item repairable forever at a hundred percent.
                if (repairing.getDamage() == 0) {
                    repairingI = -1;
                    slowTimer = 0;
                    return;
                }

                target = 100.0;
            }
            else if (!needsRepair(repairing, maxThreshold.get())) {
                repairingI = -1;
                return;
            }
            else {
                target = maxThreshold.get();
            }

            // Full speed under seventy-five percent, one at a time past it, whatever
            // brought us here. This sits on the throw itself rather than in the
            // choosing above, because the elytra rotation reaches it by its own
            // branch and would otherwise keep its old rate.
            if (slowFinish.get() && durabilityPercent(repairing) >= SLOW_ABOVE) {
                if (slowTimer > 0) {
                    slowTimer--;
                    return;
                }

                slowTimer = SLOW_INTERVAL;
            }

            FindItemResult exp = InvUtils.find(Items.EXPERIENCE_BOTTLE);

            if (exp.found()) {
                if (!exp.isHotbar() && !exp.isOffhand()) {
                    if (!replenish.get()) return;
                    InvUtils.move().from(exp.slot()).toHotbar(slot.get() - 1);
                }

                Rotations.rotate(mc.player.getYaw(), 90, () -> {
                    if (exp.getHand() != null) {
                        mc.interactionManager.interactItem(mc.player, exp.getHand());
                    }
                    else {
                        InvUtils.swap(exp.slot(), true);
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        InvUtils.swapBack();
                    }
                });
            }
        }
    }

    // Throwing

    private boolean needsRepair(ItemStack itemStack, double threshold) {
        if (itemStack.isEmpty() || !Utils.hasEnchantments(itemStack, Enchantments.MENDING)) return false;
        return durabilityPercent(itemStack) <= threshold;
    }

    // Elytra rotation

    private boolean elytraRotationActive() {
        // Hands only never repairs the chest slot, so a swapped in elytra would just stay broken.
        return autoSwapElytra.get() && mode.get() != Mode.Hands;
    }

    private boolean elytraWantsRepair(ItemStack stack) {
        if (stack.isEmpty() || !Utils.hasEnchantments(stack, Enchantments.MENDING)) return false;
        return durabilityPercent(stack) < swapAt.get();
    }

    private boolean rotateElytra() {
        ItemStack worn = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (worn.getItem() != Items.ELYTRA) return false;

        double wornPercent = durabilityPercent(worn);
        if (wornPercent < swapAt.get()) return false;

        // The swap writes straight into the armor slot, and that slot only has a known id while
        // the player inventory is the open handler. With a chest or a shulker open the id comes
        // back as -1, which the server reads as a throw, so the elytra would land on the ground.
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return false;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return false;
        // Trading a working elytra for a broken one mid air drops you out of the sky.
        if (mc.player.isGliding()) return false;

        int candidate = findRotationCandidate(wornPercent);
        if (candidate == -1) return false;

        double candidatePercent = durabilityPercent(mc.player.getInventory().getStack(candidate));

        // Pickup on the source, pickup on the armor slot, pickup back on the source: the broken
        // elytra ends up worn and the repaired one lands in the slot it came from.
        InvUtils.move().from(candidate).toArmor(EquipmentSlot.CHEST.getEntitySlotId());

        repairingI = -1;
        swapTimer = swapDelay.get();

        if (notifySwap.get()) {
            info("Now wearing an elytra at %.1f%%, %d damaged left.", candidatePercent, countRotationCandidates());
        }

        return true;
    }

    private int findRotationCandidate(double wornPercent) {
        int best = -1;
        double bestPercent = Double.MAX_VALUE;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!isRotatableElytra(stack)) continue;

            double percent = durabilityPercent(stack);
            if (percent >= wornPercent) continue;

            if (percent < bestPercent) {
                bestPercent = percent;
                best = i;
            }
        }

        return best;
    }

    private int countRotationCandidates() {
        int count = 0;

        for (int i = 0; i < 36; i++) {
            if (isRotatableElytra(mc.player.getInventory().getStack(i))) count++;
        }

        return count;
    }

    private boolean isRotatableElytra(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != Items.ELYTRA) return false;
        if (!Utils.hasEnchantments(stack, Enchantments.MENDING)) return false;
        return durabilityPercent(stack) <= swapBelow.get();
    }

    private double durabilityPercent(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) return 100.0;
        return (stack.getMaxDamage() - stack.getDamage()) / (double) stack.getMaxDamage() * 100.0;
    }

    public enum Mode {
        Armor,
        Hands,
        Both
    }
}
