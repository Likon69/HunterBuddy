package com.hunterbuddy.hud.movement;

import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.item.ItemStack;

record StatusEntry(String text, Color color, ItemStack icon) {
}
