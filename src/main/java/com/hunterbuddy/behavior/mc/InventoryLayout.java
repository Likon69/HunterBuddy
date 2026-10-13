package com.hunterbuddy.behavior.mc;

import com.hunterbuddy.behavior.farmer.FarmerProfile;
import com.hunterbuddy.util.Rockets;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;

public final class InventoryLayout {
    private final WorldReader world;
    private final List<Predicate<ItemStack>> plan;

    public InventoryLayout(WorldReader world, FarmerProfile profile) {
        this.world = world;
        net.minecraft.registry.tag.TagKey<net.minecraft.item.Item> toolTag = switch (profile.tool()) {
            case "shovel" -> ItemTags.SHOVELS;
            case "axe" -> ItemTags.AXES;
            default -> ItemTags.PICKAXES;
        };
        plan = List.of(stack -> stack.isIn(toolTag) && !stack.willBreakNextUse()
                && (!silkPickaxe(stack) || find(candidate -> candidate.isIn(toolTag) && !silkPickaxe(candidate) && !candidate.willBreakNextUse(), 0) < 0),
            InventoryLayout::silkPickaxe, stack -> stack.contains(DataComponentTypes.FOOD), Rockets::isPlain,
            stack -> stack.isOf(Items.ENDER_CHEST), stack -> stack.isOf(Items.COBBLESTONE) || stack.isOf(Items.COBBLED_DEEPSLATE)
                || stack.isOf(Items.NETHERRACK) || stack.isOf(Items.OBSIDIAN) && world.count(Items.OBSIDIAN) > 25);
    }

    public static boolean silkPickaxe(ItemStack stack) {
        return !stack.isEmpty() && stack.isIn(ItemTags.PICKAXES) && !stack.willBreakNextUse()
            && stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT)
                .getEnchantmentEntries().stream().anyMatch(entry -> entry.getKey().matchesKey(Enchantments.SILK_TOUCH) && entry.getIntValue() > 0);
    }

    public int find(Predicate<ItemStack> filter, int start) {
        for (int i = start; i < 36; i++) if (filter.test(stack(i))) return i;
        return -1;
    }

    public ItemStack stack(int index) { return world.client().player.getInventory().getStack(index); }
    public int freeSlot() { return find(ItemStack::isEmpty, 0); }
    public int size() { return plan.size(); }
    public Predicate<ItemStack> filter(int slot) { return plan.get(slot); }
}
