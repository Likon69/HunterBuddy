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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public class AutoEXPPlus extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgElytra = settings.createGroup("Elytra Rotation");
    private final SettingGroup sgThrow = settings.createGroup("Throwing");

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

    private final Setting<Boolean> ignoreElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-elytra")
        .description("Ignore elytra when repairing. Has no effect while auto-swap-elytra is on.")
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

    private final Setting<Boolean> avoidOverthrow = sgThrow.add(new BoolSetting.Builder()
        .name("avoid-overthrow")
        .description("Keep no more owed in the air than the item still has room for, and stop overlapping throws over ninety percent, where a bottle too many is a bottle wasted. Off, the module throws one every tick until the server says the item is full, which is a good twenty bottles later.")
        .defaultValue(true)
        .build()
    );

    private static final int CHEST_INDEX = SlotUtils.ARMOR_START + EquipmentSlot.CHEST.getEntitySlotId();

    // A bottle spawns 3 + rand(5) + rand(5) experience, and mending turns each point
    // into two durability: six at worst, twenty-two at best. Twenty-two is the figure
    // to reckon with, because it is the one that cannot be exceeded -- a bottle held
    // back on that count was never needed.
    private static final int MAX_REPAIR_PER_BOTTLE = 22;

    // A bottle that has not paid up by now never will. Its orbs went to another
    // mending item, or they broke somewhere they could not follow you back from.
    private static final int FLIGHT_TICKS = 20;

    // Above this much durability the throws stop overlapping: one bottle, wait for
    // its answer, next bottle. The last tenth is where an extra bottle in the air is
    // most likely to arrive at an item that is already full, and there is little
    // enough left that going one at a time costs a second, not a minute.
    private static final int SLOW_ABOVE = 90;

    // Bottles allowed to come back with nothing at all behind them before the module
    // stops trusting the ground under it.
    private static final int PROBE_AFTER = 3;

    // And how long it leaves between the single bottles it sends after that, to find
    // out whether the orbs can reach you again.
    private static final int PROBE_EVERY = 40;

    private int repairingI;
    private int swapTimer;

    // One entry per bottle in the air, the two lists side by side: how long ago it
    // left the hand, and how much durability it can still owe.
    private final IntArrayList flying = new IntArrayList();
    private final IntArrayList owed = new IntArrayList();

    private int trackedSlot;
    private Item trackedItem;
    private int trackedMax;
    private int lastDamage;

    // Bottles that ran out of time without a single point of durability behind them,
    // and ticks since the last one left, so the probe does not fire every second.
    private int unpaid;
    private int sinceThrow;

    public AutoEXPPlus() {
        super(HunterBuddyAddon.UTILITY_CATEGORY, "auto-exp-plus", "Automatically repairs your armor and tools in pvp.");
    }

    @Override
    public void onActivate() {
        repairingI = -1;
        swapTimer = 0;
        flying.clear();
        owed.clear();
        unpaid = 0;
        sinceThrow = PROBE_EVERY;
        trackedSlot = -1;
        trackedItem = null;
        trackedMax = 0;
        lastDamage = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        age();
        if (sinceThrow < PROBE_EVERY) sinceThrow++;

        // Nothing goes out while an elytra is changing hands. The bottles already in
        // the air will mend whatever ends up in the chest slot, and the ones thrown
        // now would arrive with the inventory still moving under them.
        if (swapTimer > 0) {
            swapTimer--;
            return;
        }

        if (elytraRotationActive() && rotateElytra()) return;

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
            else if (!needsRepair(repairing, maxThreshold.get())) {
                repairingI = -1;
                return;
            }
            else {
                target = maxThreshold.get();
            }

            // Every tick, whether or not a bottle goes out: the accounting is about
            // what the server sends back, not about what we do.
            observe(repairing);
            if (!worthThrowing(repairing, target)) return;

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

                flying.add(0);
                owed.add(MAX_REPAIR_PER_BOTTLE);
                sinceThrow = 0;
            }
        }
    }

    // Throwing

    /**
     * Ages the bottles in the air and writes off the ones that will never answer.
     *
     * <p>Runs every tick and not only while something is being repaired: bottles
     * outlive the item they were thrown for, and an elytra swap in between must
     * not make the module forget what it already sent.
     */
    private void age() {
        for (int i = flying.size() - 1; i >= 0; i--) {
            int age = flying.getInt(i) + 1;

            if (age < FLIGHT_TICKS) {
                flying.set(i, age);
                continue;
            }

            // Out of time still owing every point it was ever worth: nothing of it
            // came back at all. That is the shape of a bottle whose orbs are sitting
            // where it broke, too far behind to follow.
            if (owed.getInt(i) == MAX_REPAIR_PER_BOTTLE) unpaid++;

            flying.removeInt(i);
            owed.removeInt(i);
        }
    }

    /**
     * Pays the bottles in the air down with the durability the server sends back.
     *
     * <p>A bottle is not a repair. It is an entity that has to fly, break, spawn
     * orbs, and have those orbs travel back to you and be picked up two ticks
     * apart -- half a second at best, during which the item on the client still
     * reads exactly as damaged as it was. Throw one every tick and twenty are in
     * the air for an item that needed six.
     *
     * <p>So a throw is booked as a debt of twenty-two, the most a bottle can ever
     * be worth, and every point that lands pays that debt down. Booking the debt
     * and waiting for a whole bottle's worth to write it off, the way this used to,
     * is what made the module crawl: an average bottle is worth fourteen, so the
     * debt was never settled, the bottle sat in the air until it timed out, and for
     * those twenty ticks the module thought it had twenty-two points coming that it
     * had already been paid. Paying by the point instead means the room in the item
     * reopens as the orbs actually arrive.
     */
    private void observe(ItemStack repairing) {
        int damage = repairing.getDamage();

        // The slot is not the item. Put a fresher pickaxe in the slot the module was watching
        // and the damage drops by three hundred in one tick without a single orb having
        // arrived; read as repair it would clear every debt at once and let the next tick
        // throw a full load at an item that never asked for one. So the item itself is
        // watched, and a different one starts the reading over rather than paying anything.
        if (trackedSlot != repairingI || repairing.getItem() != trackedItem || repairing.getMaxDamage() != trackedMax) {
            trackedSlot = repairingI;
            trackedItem = repairing.getItem();
            trackedMax = repairing.getMaxDamage();
        }
        else if (damage < lastDamage) credit(lastDamage - damage);

        lastDamage = damage;
    }

    /** Puts durability that has landed against the oldest debts first: they left first. */
    private void credit(int repaired) {
        unpaid = 0;

        // No more than what is actually out there can have come from out there. Mending also
        // pays from orbs you walked over, and from a second bottle thrower, and this is what
        // keeps that from writing off flights that are still owed.
        repaired = Math.min(repaired, promised());

        for (int i = 0; i < owed.size() && repaired > 0; i++) {
            int take = Math.min(repaired, owed.getInt(i));

            owed.set(i, owed.getInt(i) - take);
            repaired -= take;
        }

        // A bottle with nothing left to give is not standing in the way any more.
        for (int i = owed.size() - 1; i >= 0; i--) {
            if (owed.getInt(i) == 0) {
                owed.removeInt(i);
                flying.removeInt(i);
            }
        }
    }

    /** Durability the bottles in the air can still be worth, all of them at their best. */
    private int promised() {
        int total = 0;

        for (int i = 0; i < owed.size(); i++) total += owed.getInt(i);

        return total;
    }

    /** True while a bottle is out there that has not repaired a single point yet. */
    private boolean awaitingAnswer() {
        for (int i = 0; i < owed.size(); i++) {
            if (owed.getInt(i) == MAX_REPAIR_PER_BOTTLE) return true;
        }

        return false;
    }

    private boolean worthThrowing(ItemStack repairing, double targetPercent) {
        if (!avoidOverthrow.get()) return true;

        // Three bottles that came back with nothing at all: the orbs are spawning
        // where the bottle broke and staying there -- under an elytra at speed, over
        // a drop, behind a wall. One bottle every two seconds from here, purely to
        // find out when that stops being true. The first point that lands clears the
        // count and the pace comes straight back.
        if (unpaid >= PROBE_AFTER && (awaitingAnswer() || sinceThrow < PROBE_EVERY)) return false;

        // Near the top the item can no longer absorb a bad guess, so the throws stop
        // overlapping: one bottle, wait until it answers, then the next. Below that
        // line the debt below is what holds the pace.
        if (durabilityPercent(repairing) >= SLOW_ABOVE && awaitingAnswer()) return false;

        // What is owed, at the most it could still be worth, against what is left to
        // fill. One more bottle only goes out if even the best case leaves the item
        // short.
        return promised() < repairing.getDamage() - targetDamage(repairing, targetPercent);
    }

    /** The damage value the item is being repaired down to. */
    private int targetDamage(ItemStack stack, double percent) {
        if (stack.getMaxDamage() <= 0) return 0;
        return (int) Math.floor(stack.getMaxDamage() * (1.0 - percent / 100.0));
    }

    private boolean needsRepair(ItemStack itemStack, double threshold) {
        if (itemStack.isEmpty() || !Utils.hasEnchantments(itemStack, Enchantments.MENDING)) return false;
        return (itemStack.getMaxDamage() - itemStack.getDamage()) / (double) itemStack.getMaxDamage() * 100 <= threshold;
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
        // Another elytra, read from scratch. The debts stay: those orbs will mend
        // whatever is in the chest slot when they arrive.
        trackedSlot = -1;
        trackedItem = null;

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
