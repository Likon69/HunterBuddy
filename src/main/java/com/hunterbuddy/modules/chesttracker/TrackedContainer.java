package com.hunterbuddy.modules.chesttracker;

import com.hunterbuddy.modules.regear.util.ShulkerDataParser;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.registry.Registries;

public class TrackedContainer {
   private final BlockPos position;
   private final String dimension;
   private String customName;
   private final Map<String, Integer> items;
   private final List<ItemStack> itemStacks;
   private long lastUpdated;
   private String containerType;

   public TrackedContainer(BlockPos position, String dimension, String containerType) {
      this.position = position;
      this.dimension = dimension;
      this.containerType = containerType;
      this.items = new HashMap<>();
      this.itemStacks = new ArrayList<>();
      this.customName = null;
      this.lastUpdated = System.currentTimeMillis();
   }

   public void updateContents(List<ItemStack> stacks) {
      this.items.clear();
      this.itemStacks.clear();

      for (ItemStack stack : stacks) {
         if (stack != null && !stack.isEmpty()) {
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            this.items.put(itemId, this.items.getOrDefault(itemId, 0) + stack.getCount());
            this.itemStacks.add(stack.copy());
            this.indexNestedItems(stack);
         }
      }

      this.lastUpdated = System.currentTimeMillis();
   }

   private void indexNestedItems(ItemStack stack) {
      if (stack.getItem() instanceof BlockItem blockItem) {
         if (blockItem.getBlock() instanceof ShulkerBoxBlock) {
            Map<Item, Integer> nestedContents = ShulkerDataParser.parseShulkerContents(stack);

            for (Entry<Item, Integer> entry : nestedContents.entrySet()) {
               String itemId = Registries.ITEM.getId(entry.getKey()).toString();
               this.items.put(itemId, this.items.getOrDefault(itemId, 0) + entry.getValue());
            }
         }
      }
   }

   public boolean containsItem(String itemId) {
      return this.items.containsKey(itemId);
   }

   public boolean containsItem(Item item) {
      String itemId = Registries.ITEM.getId(item).toString();
      return this.items.containsKey(itemId);
   }

   public int getItemCount(String itemId) {
      return this.items.getOrDefault(itemId, 0);
   }

   public Map<String, Integer> getItems() {
      return new HashMap<>(this.items);
   }

   public List<ItemStack> getItemStacks() {
      return new ArrayList<>(this.itemStacks);
   }

   public BlockPos getPosition() {
      return this.position;
   }

   public String getDimension() {
      return this.dimension;
   }

   public String getCustomName() {
      return this.customName;
   }

   public void setCustomName(String name) {
      this.customName = name;
   }

   public long getLastUpdated() {
      return this.lastUpdated;
   }

   public String getContainerType() {
      return this.containerType;
   }

   public boolean isEmpty() {
      return this.items.isEmpty();
   }

   public JsonObject toJson() {
      JsonObject json = new JsonObject();
      json.addProperty("x", this.position.getX());
      json.addProperty("y", this.position.getY());
      json.addProperty("z", this.position.getZ());
      json.addProperty("dimension", this.dimension);
      json.addProperty("type", this.containerType);
      json.addProperty("lastUpdated", this.lastUpdated);
      if (this.customName != null) {
         json.addProperty("customName", this.customName);
      }

      JsonObject itemsJson = new JsonObject();

      for (Entry<String, Integer> entry : this.items.entrySet()) {
         itemsJson.addProperty(entry.getKey(), entry.getValue());
      }

      json.add("items", itemsJson);
      return json;
   }

   public static TrackedContainer fromJson(JsonObject json) {
      BlockPos pos = new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt());
      String dimension = json.get("dimension").getAsString();
      String type = json.has("type") ? json.get("type").getAsString() : "chest";
      TrackedContainer container = new TrackedContainer(pos, dimension, type);
      if (json.has("customName")) {
         container.customName = json.get("customName").getAsString();
      }

      if (json.has("lastUpdated")) {
         container.lastUpdated = json.get("lastUpdated").getAsLong();
      }

      if (json.has("items")) {
         JsonObject itemsJson = json.getAsJsonObject("items");

         for (String key : itemsJson.keySet()) {
            container.items.put(key, itemsJson.get(key).getAsInt());
         }
      }

      return container;
   }

   public String getDisplayName() {
      return this.customName != null && !this.customName.isEmpty()
         ? this.customName
         : String.format(
            "%s [%d, %d, %d]",
            this.containerType.substring(0, 1).toUpperCase() + this.containerType.substring(1),
            this.position.getX(),
            this.position.getY(),
            this.position.getZ()
         );
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return !(obj instanceof TrackedContainer other) ? false : this.position.equals(other.position) && this.dimension.equals(other.dimension);
      }
   }

   @Override
   public int hashCode() {
      return this.position.hashCode() * 31 + this.dimension.hashCode();
   }
}
