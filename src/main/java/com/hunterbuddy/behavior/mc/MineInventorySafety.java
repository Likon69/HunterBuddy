package com.hunterbuddy.behavior.mc;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class MineInventorySafety {
    private MineInventorySafety() {}

    public static boolean room(MineSiteContext mine, Block block) {
        Set<Item> possible = new HashSet<>();
        possible.add(block.asItem());
        if (block == Blocks.GRAVEL) possible.add(Items.FLINT);
        if (block == Blocks.STONE) possible.add(Items.COBBLESTONE);
        if (block == Blocks.DEEPSLATE) possible.add(Items.COBBLED_DEEPSLATE);
        if (block == Blocks.GRASS_BLOCK || block == Blocks.COARSE_DIRT) possible.add(Items.DIRT);
        int free = 0;
        for (int slot = 0; slot < 36; slot++) if (mine.world.client().player.getInventory().getStack(slot).isEmpty()) free++;
        boolean known = mine.profile.blocks().contains(block) || block == Blocks.STONE || block == Blocks.DEEPSLATE;
        if (free >= (known ? 2 : 4)) return true;
        if (!known) return false;
        if (free < 1) return false;
        for (Item item : possible) {
            boolean fits = false;
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = mine.world.client().player.getInventory().getStack(slot);
                if (stack.isOf(item) && stack.getMaxCount() - stack.getCount() >= 16) fits = true;
            }
            if (!fits) return false;
        }
        return true;
    }
}
