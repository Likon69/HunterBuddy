package com.hunterbuddy.modules.mixin.accessors;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.collection.DefaultedList;

@Mixin(PlayerInventory.class)
public interface PlayerInventoryAccessor {
   @Accessor("selectedSlot")
   int getSelectedSlot();

   @Accessor("selectedSlot")
   void setSelectedSlot(int var1);

   @Accessor("main")
   DefaultedList<ItemStack> getMain();
}
