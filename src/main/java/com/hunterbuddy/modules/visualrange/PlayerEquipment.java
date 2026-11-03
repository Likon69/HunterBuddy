package com.hunterbuddy.modules.visualrange;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

public final class PlayerEquipment {
    private static final String NETHERITE_PREFIX = "netherite_";
    private static final String SEPARATOR = ", ";

    private PlayerEquipment() {
    }

    public static String describe(PlayerEntity player) {
        List<String> parts = new ArrayList<>();
        for (EquipmentSlot slot : armorSlots()) addName(parts, player.getEquippedStack(slot), "");
        addName(parts, player.getStackInHand(Hand.MAIN_HAND), " (hand)");
        addName(parts, player.getStackInHand(Hand.OFF_HAND), " (off)");
        return String.join(SEPARATOR, parts);
    }

    public static String tags(PlayerEntity player) {
        List<ItemStack> worn = worn(player);
        List<String> tags = new ArrayList<>();
        if (worn.stream().anyMatch(PlayerEquipment::isNetherite)) tags.add("netherite");
        if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) tags.add("elytra");
        if (worn.stream().anyMatch(stack -> stack.isOf(Items.TOTEM_OF_UNDYING))) tags.add("totem");
        return String.join(SEPARATOR, tags);
    }

    private static List<EquipmentSlot> armorSlots() {
        return List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    }

    private static List<ItemStack> worn(PlayerEntity player) {
        List<ItemStack> stacks = new ArrayList<>();
        for (EquipmentSlot slot : armorSlots()) stacks.add(player.getEquippedStack(slot));
        stacks.add(player.getStackInHand(Hand.MAIN_HAND));
        stacks.add(player.getStackInHand(Hand.OFF_HAND));
        return stacks;
    }

    private static boolean isNetherite(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).getPath().startsWith(NETHERITE_PREFIX);
    }

    private static void addName(List<String> parts, ItemStack stack, String suffix) {
        if (!stack.isEmpty()) parts.add(stack.getName().getString() + suffix);
    }
}
