package com.hunterbuddy.modules.regear.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemConvertible;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class ShulkerDataParser {
   public static Map<Item, Integer> parseShulkerContents(ItemStack shulkerStack) {
      Map<Item, Integer> itemCounts = new HashMap<>();
      ContainerComponent container = (ContainerComponent)shulkerStack.get(DataComponentTypes.CONTAINER);
      if (container != null) {
         for (ItemStack itemStack : container.stream().toList()) {
            if (!itemStack.isEmpty()) {
               itemCounts.merge(itemStack.getItem(), itemStack.getCount(), Integer::sum);
            }
         }

         if (!itemCounts.isEmpty()) {
            return itemCounts;
         }
      }

      NbtComponent customData = (NbtComponent)shulkerStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
      if (customData != null && !customData.isEmpty()) {
         NbtCompound nbt = customData.copyNbt();
         if (nbt != null && nbt.contains("BlockEntityTag")) {
            Optional<NbtCompound> optional = nbt.getCompound("BlockEntityTag");
            if (optional.isPresent()) {
               NbtCompound blockEntityTag = optional.get();
               if (blockEntityTag.contains("Items")) {
                  Optional<NbtList> itemsListOpt = blockEntityTag.getList("Items");
                  if (itemsListOpt.isPresent()) {
                     NbtList items = itemsListOpt.get();

                     for (int i = 0; i < items.size(); i++) {
                        Optional<NbtCompound> itemOpt = items.getCompound(i);
                        if (itemOpt.isPresent()) {
                           ItemStack parsed = parseItemFromNbt(itemOpt.get());
                           if (!parsed.isEmpty()) {
                              itemCounts.merge(parsed.getItem(), parsed.getCount(), Integer::sum);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return itemCounts;
   }

   public static List<ItemStack> parseShulkerContentsAsList(ItemStack shulkerStack) {
      List<ItemStack> items = new ArrayList<>();
      ContainerComponent container = (ContainerComponent)shulkerStack.get(DataComponentTypes.CONTAINER);
      if (container != null) {
         container.stream().forEach(stack -> {
            if (stack != null && !stack.isEmpty()) {
               items.add(stack.copy());
            }
         });
         if (!items.isEmpty()) {
            return items;
         }
      }

      Map<Item, Integer> counts = parseShulkerContents(shulkerStack);

      for (Entry<Item, Integer> entry : counts.entrySet()) {
         int count = entry.getValue();
         int maxStack = entry.getKey().getDefaultStack().getMaxCount();

         while (count > 0) {
            int stackSize = Math.min(count, maxStack);
            items.add(new ItemStack((ItemConvertible)entry.getKey(), stackSize));
            count -= stackSize;
         }
      }

      return items;
   }

   private static ItemStack parseItemFromNbt(NbtCompound itemTag) {
      String id = itemTag.getString("id", "");
      if (id.isEmpty()) {
         return ItemStack.EMPTY;
      }

      int count = 1;
      if (itemTag.contains("count")) {
         count = itemTag.getInt("count", 1);
      } else if (itemTag.contains("Count")) {
         count = itemTag.getByte("Count", (byte)1);
      }

      Identifier itemId = Identifier.tryParse(id);
      if (itemId == null) {
         return ItemStack.EMPTY;
      }

      Item item = (Item)Registries.ITEM.get(itemId);
      return item != null && item != Registries.ITEM.get(Registries.ITEM.getDefaultId())
         ? new ItemStack(item, count)
         : ItemStack.EMPTY;
   }
}
