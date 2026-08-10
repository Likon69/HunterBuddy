package com.hunterbuddy.util;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * How much gliding a player is actually carrying.
 *
 * <p>Lives on its own because two HUDs need the same answer and the answer is not obvious.
 * The status line reports it; the travel estimate checks an arrival time against it. Written
 * twice, the two copies would drift the first time either was touched — and this formula has
 * already been wrong once, in a way that took a bytecode read and a datapack read to settle.
 *
 * <p>Nothing here reads a setting. The reserve is a parameter so each caller keeps its own.
 */
public final class ElytraFlightMath {
    private ElytraFlightMath() {
    }

    /**
     * Seconds of gliding left in a stack, or 0 when it is at or under the reserve.
     *
     * <p>A gliding item takes one point of damage every twenty ticks — the wear fires when
     * {@code fallFlyTicks % 10 == 0} and the resulting count is even — so on a bare elytra a
     * durability point is a second. Fireworks do not enter into it: boosting covers more
     * ground in the same seconds, it does not spend the elytra faster.
     *
     * <p>Unbreaking multiplies that, and by which factor depends on a tag. The vanilla
     * {@code unbreaking.json} carries two effects chosen by membership in
     * {@code #enchantable/armor}; the elytra is not in it — {@code #chest_armor} lists the
     * seven chestplates and nothing else — so it takes the non-armor branch, which cancels
     * the wear with probability {@code L/(L+1)}. At level three, three wear events in four
     * are cancelled and a point covers four seconds. Had the elytra been tagged as armor the
     * factor would have been about 1.4 instead.
     *
     * <p>Probabilistic, so this is an average rather than a guarantee to the second.
     *
     * @param reservePct durability kept back, as a percentage, so a planned leg never ends on
     *                   a breaking chestplate
     */
    public static int flightSecondsOf(ItemStack stack, double reservePct) {
        if (!stack.isOf(Items.ELYTRA)) return 0;

        int max = stack.getMaxDamage();
        if (max <= 0) return 0;

        int reserve = Math.max(1, (int) Math.ceil(max * reservePct / 100.0));
        int usable = Math.max(0, max - stack.getDamage() - reserve);

        return usable * (unbreakingLevelOf(stack) + 1);
    }

    /**
     * Unbreaking level on this stack, or 0.
     *
     * <p>Read per stack on purpose: spares in a bag rarely all carry the same level, and one
     * shared multiplier would be wrong for most of them.
     */
    public static int unbreakingLevelOf(ItemStack stack) {
        for (var entry : stack.getEnchantments().getEnchantmentEntries()) {
            if (entry.getKey().matchesKey(Enchantments.UNBREAKING)) return entry.getIntValue();
        }

        return 0;
    }

    /**
     * Gliding seconds across every usable elytra the player carries, the worn one included.
     *
     * <p>Bounded to the backpack rather than to {@code PlayerInventory.size()}: that size is
     * {@code main.size() + EQUIPMENT_SLOTS.size()}, so walking it would pass over the chest
     * slot and count the worn elytra a second time on top of the explicit add.
     */
    public static int totalUsableSeconds(PlayerEntity player, double reservePct) {
        if (player == null) return 0;

        int total = 0;
        var inventory = player.getInventory().getMainStacks();

        for (int i = 0; i < inventory.size(); i++) {
            total += flightSecondsOf(inventory.get(i), reservePct);
        }

        return total + flightSecondsOf(player.getEquippedStack(EquipmentSlot.CHEST), reservePct);
    }

    /** Wearable elytras the player carries, the worn one included. */
    public static int countUsableElytras(PlayerEntity player, double reservePct) {
        if (player == null) return 0;

        int count = 0;
        var inventory = player.getInventory().getMainStacks();

        for (int i = 0; i < inventory.size(); i++) {
            if (flightSecondsOf(inventory.get(i), reservePct) > 0) count++;
        }

        if (flightSecondsOf(player.getEquippedStack(EquipmentSlot.CHEST), reservePct) > 0) count++;

        return count;
    }
}
