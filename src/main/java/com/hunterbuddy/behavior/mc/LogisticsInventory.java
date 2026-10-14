package com.hunterbuddy.behavior.mc;

import com.google.gson.JsonParser;
import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.util.GliderSwap;
import com.hunterbuddy.util.Rockets;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.collection.DefaultedList;

public final class LogisticsInventory {
    public static final double TOOL_WEAR_THRESHOLD = 0.02;
    public static final double ELYTRA_WEAR_THRESHOLD = 0.05;
    private LogisticsInventory() {}

    public static boolean healthy(ItemStack stack) {
        return !stack.isEmpty() && (!stack.isDamageable()
            || remaining(stack) > (anyTool(stack) ? TOOL_WEAR_THRESHOLD : ELYTRA_WEAR_THRESHOLD));
    }

    public static double remaining(ItemStack stack) {
        return stack.getMaxDamage() == 0 ? 1 : 1.0 - (double) stack.getDamage() / stack.getMaxDamage();
    }

    public static boolean wornTool(ItemStack stack) {
        return anyTool(stack) && !stack.isIn(ItemTags.SWORDS) && remaining(stack) <= TOOL_WEAR_THRESHOLD;
    }

    public static boolean tool(ItemStack stack, FarmerProfile profile) {
        return stack.isIn(switch (profile.tool()) {
            case "shovel" -> ItemTags.SHOVELS;
            case "axe" -> ItemTags.AXES;
            default -> ItemTags.PICKAXES;
        });
    }

    public static boolean anyTool(ItemStack stack) {
        return stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.HOES)
            || stack.isIn(ItemTags.SWORDS);
    }

    public static boolean shelterBlock(ItemStack stack) {
        return stack.isOf(Items.COBBLESTONE) || stack.isOf(Items.COBBLED_DEEPSLATE)
            || stack.isOf(Items.NETHERRACK) || stack.isOf(Items.DIRT) || stack.isOf(Items.STONE);
    }

    public static boolean protection(ItemStack stack) {
        return stack.isOf(Items.OBSIDIAN) || stack.isOf(Items.COBBLESTONE) || stack.isOf(Items.COBBLED_DEEPSLATE) ||
            stack.isOf(Items.NETHERRACK) || stack.isOf(Items.DIRT) || stack.isOf(Items.STONE);
    }

    public static EquipmentSlot armorSlot(ItemStack stack) {
        EquippableComponent component = stack.get(DataComponentTypes.EQUIPPABLE);
        if (component == null) return null;
        return switch (component.slot()) {
            case HEAD, CHEST, LEGS, FEET -> component.slot();
            default -> null;
        };
    }

    public static boolean equipment(ItemStack stack) {
        return anyTool(stack) || stack.contains(DataComponentTypes.FOOD) || Rockets.isPlain(stack) || armorSlot(stack) != null
            || GliderSwap.isGlider(stack) || protection(stack) || stack.isOf(Items.ENDER_CHEST) || ShulkerSignature.isShulker(stack);
    }

    public static Predicate<ItemStack> accepted(FarmerProfile profile) {
        return stack -> !stack.isEmpty() && profile.acceptedItems().contains(stack.getItem());
    }

    public static Predicate<ItemStack> miscellaneous(FarmerProfile profile) {
        return stack -> !stack.isEmpty() && !accepted(profile).test(stack) && !equipment(stack);
    }

    public static int profileTools(WorldReader world, FarmerProfile profile) {
        int count = world.countMatching(stack -> tool(stack, profile) && healthy(stack));
        if (!profile.tool().equals("shovel") && !profile.tool().equals("axe") && world.countMatching(stack ->
            InventoryLayout.silkPickaxe(stack) && healthy(stack)) > 0) count--;
        return count;
    }

    public static boolean pure(ShulkerSignature signature, FarmerProfile profile) {
        return signature.contents().stream().filter(stack -> !stack.isEmpty()).allMatch(accepted(profile));
    }

    public static boolean full(ShulkerSignature signature, FarmerProfile profile) {
        return pure(signature, profile) && signature.contents().stream().allMatch(stack -> !stack.isEmpty() && stack.getCount() ==
            stack.getMaxCount());
    }

    public static boolean reserve(ShulkerSignature signature) {
        List<ItemStack> contents = signature.contents().stream().filter(stack -> !stack.isEmpty()).toList();
        return !contents.isEmpty() && contents.stream().allMatch(stack -> anyTool(stack) || stack.contains(DataComponentTypes.FOOD) ||
            armorSlot(stack) != null || GliderSwap.isGlider(stack));
    }

    public static List<ItemStack> player(WorldReader world) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < 36; i++) stacks.add(world.client().player.getInventory().getStack(i).copy());
        return stacks;
    }

    public static String encode(List<ItemStack> stacks, WorldReader world) {
        return ContainerComponent.CODEC.encodeStart(world.client().world.getRegistryManager().getOps(JsonOps.INSTANCE),
            ContainerComponent.fromStacks(stacks)).getOrThrow().toString();
    }

    public static List<ItemStack> decode(String encoded, int size, WorldReader world) {
        ContainerComponent component =
            ContainerComponent.CODEC.parse(world.client().world.getRegistryManager().getOps(JsonOps.INSTANCE),
            JsonParser.parseString(encoded)).getOrThrow();
        DefaultedList<ItemStack> contents = DefaultedList.ofSize(size, ItemStack.EMPTY);
        component.copyTo(contents);
        return List.copyOf(contents);
    }

    public static boolean balanced(List<ItemStack> beforePlayer, List<ItemStack> beforeContainer, List<ItemStack> player,
        List<ItemStack> container) {
        List<ItemStack> kinds = new ArrayList<>();
        for (List<ItemStack> stacks : List.of(beforePlayer, beforeContainer, player, container)) for (ItemStack stack : stacks)
            if (!stack.isEmpty() && kinds.stream().noneMatch(kind -> ItemStack.areItemsAndComponentsEqual(kind, stack))) kinds.add(stack);
        for (ItemStack kind : kinds) if (count(beforePlayer, kind) + count(beforeContainer, kind) != count(player, kind) +
            count(container, kind)) return false;
        return true;
    }

    private static int count(List<ItemStack> stacks, ItemStack kind) {
        return stacks.stream().filter(stack -> ItemStack.areItemsAndComponentsEqual(stack, kind)).mapToInt(ItemStack::getCount).sum();
    }

    public static boolean equal(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.areEqual(a.get(i), b.get(i))) return false;
        return true;
    }

    public static boolean authorizedTransfer(List<ItemStack> beforePlayer, List<ItemStack> beforeContainer, List<ItemStack> player,
        List<ItemStack> container, ItemStack expected, boolean deposit) {
        if (!balanced(beforePlayer, beforeContainer, player, container)) return false;
        for (int group = 0; group < 2; group++) {
            List<ItemStack> before = group == 0 ? beforePlayer : beforeContainer;
            List<ItemStack> after = group == 0 ? player : container;
            for (int i = 0; i < before.size(); i++) {
                ItemStack old = before.get(i);
                ItemStack next = after.get(i);
                if (!ItemStack.areEqual(old, next) && (!old.isEmpty() && !ItemStack.areItemsAndComponentsEqual(old, expected)
                    || !next.isEmpty() && !ItemStack.areItemsAndComponentsEqual(next, expected))) return false;
            }
        }
        int moved = count(container, expected) - count(beforeContainer, expected);
        if (!deposit) moved = -moved;
        return moved > 0 && moved <= expected.getCount();
    }

    public static List<String> missing(WorldReader world, FarmerProfile profile, List<ItemStack> reserve) {
        List<String> missing = new ArrayList<>();
        if (profileTools(world, profile) == 0 && reserve.stream().noneMatch(stack -> tool(stack, profile) && healthy(stack)))
            missing.add("tool " + profile.tool());
        if (world.countMatching(stack -> InventoryLayout.silkPickaxe(stack) && healthy(stack)) == 0 && reserve.stream().noneMatch(stack
            -> InventoryLayout.silkPickaxe(stack) && healthy(stack))) missing.add("Silk Touch pickaxe");
        if (world.countMatching(stack -> stack.contains(DataComponentTypes.FOOD)) < 16 && reserve.stream().noneMatch(stack ->
            stack.contains(DataComponentTypes.FOOD))) missing.add("food");
        if (world.countMatching(GliderSwap::isGlider) > 0 && world.countMatching(stack -> GliderSwap.isGlider(stack) && healthy(stack)) == 0
            && reserve.stream().noneMatch(stack -> GliderSwap.isGlider(stack) && healthy(stack))) missing.add("replacement elytra");
        return List.copyOf(missing);
    }

    public static boolean needsRegear(WorldReader world, FarmerProfile profile) {
        if (profileTools(world, profile) == 0 || world.countMatching(stack -> InventoryLayout.silkPickaxe(stack) && healthy(stack)) == 0
            || world.countMatching(stack -> stack.contains(DataComponentTypes.FOOD)) < 16) return true;
        if (world.countMatching(LogisticsInventory::wornTool) > 0) return true;
        return world.countMatching(GliderSwap::isGlider) > 0
            && world.countMatching(stack -> GliderSwap.isGlider(stack) && healthy(stack)) == 0;
    }
}
