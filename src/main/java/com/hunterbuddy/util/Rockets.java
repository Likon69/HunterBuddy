package com.hunterbuddy.util;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public final class Rockets {
   private Rockets() {
   }

   public static boolean isPlain(ItemStack stack) {
      if (stack == null || !stack.isOf(Items.FIREWORK_ROCKET)) {
         return false;
      }

      FireworksComponent component = stack.get(DataComponentTypes.FIREWORKS);
      return component == null || component.explosions().isEmpty();
   }
}
