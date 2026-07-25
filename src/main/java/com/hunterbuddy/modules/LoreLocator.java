package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.Arrays;
import java.util.Optional;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.ItemStack;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.passive.TropicalFishEntity;
import net.minecraft.entity.passive.TropicalFishEntity.Variant;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.HopperScreenHandler;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;

public class LoreLocator extends Module {
   private final SettingGroup sgRares = this.settings.createGroup("Rares Settings");
   private final SettingGroup sgUniques = this.settings.createGroup("Uniques Settings");
   private final Setting<Boolean> illegalEnchants = this.sgRares
      .add(
         new Builder().name("illegal-enchants")
                  .description("Highlight items with illegal enchantments like Mending/Infinity, or stacked Protection.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> onlySilkyShears = this.sgRares
      .add(
         new Builder().name("exclusive-silky-shears")
                     .description("Highlight silk touch shears only if they have no other enchants.")
                  .defaultValue(true)
               .visible(this.illegalEnchants::get)
            .build()
      );
   private final Setting<Boolean> onlyInfinityMending = this.sgRares
      .add(
         new Builder().name("exclusive-mending/Infinity")
                     .description("Highlight bows & books that have ONLY mending & infinity applied.")
                  .defaultValue(true)
               .visible(this.illegalEnchants::get)
            .build()
      );
   private final Setting<Boolean> negativeDurability = this.sgRares
      .add(
         new Builder().name("negative-durability")
                  .description("Highlight items with negative true durability (all negative durability items show in-game as 0 durability items in 1.21.)")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> petrifiedSlabs = this.sgRares
      .add(new Builder().name("alpha-slabs").description("Highlight alpha slabs (now petrified oak slabs.)").defaultValue(true)
            .build());
   private final Setting<Boolean> lagRockets = this.sgRares
      .add(new Builder().name("lag-rockets").description("Highlight lag rockets.").defaultValue(true).build());
   private final Setting<Boolean> illegalFish = this.sgRares
      .add(
         new Builder().name("illegal-fish")
                  .description("Highlight illegal tropical fish with black as one of their colors. These are no longer obtainable as of 1.21.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> renamedItems = this.sgUniques
      .add(new Builder().name("renamed-items").description("Highlight renamed items in GUIs.").defaultValue(false).build());
   private final Setting<Boolean> renamedShulks = this.sgUniques
      .add(
         new Builder().name("renamed-shulkers")
                     .description("Highlight renamed shulker boxes, even if they contain no renamed items.")
                  .defaultValue(false)
               .visible(this.renamedItems::get)
            .build()
      );
   private final Setting<Boolean> writtenBooks = this.sgUniques
      .add(new Builder().name("written-books").description("Highlight written books.").defaultValue(true).build());
   private final Setting<String> metadataSearch = this.settings
      .getDefaultGroup()
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                     .name("metadata-search")
                  .description("Fuzzy search for item NBT data. Notable usage examples: specific book authors, item names, or enchants.")
               .defaultValue("")
            .build()
      );
   private final Setting<Boolean> splitQueries = this.settings
      .getDefaultGroup()
      .add(
         new Builder().name("split-queries")
                  .description("Split search queries into multiple items separated by commas. Disable to treat commas literally in the search instead.")
               .defaultValue(true)
            .build()
      );
   public final Setting<SettingColor> color = this.settings
      .getDefaultGroup()
      .add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
               .name("highlight-color")
            .defaultValue(new SettingColor(254, 0, 255, 119)).build()
      );
   private final Setting<Boolean> ownInventory = this.settings
      .getDefaultGroup()
      .add(
         new Builder().name("inventory-highlight")
                  .description("Highlight items meeting the above criteria on the player inventory screen.")
               .defaultValue(true)
            .build()
      );

   public LoreLocator() {
      super(HunterBuddyAddon.HUNT_CATEGORY, "LoreLocator", "Slot highlighter for rare, unique, and anomalous items.");
   }

   private int enchantmentsCount(ItemStack stack) {
      int count = 0;
      if (!stack.isEmpty()) {
         count = stack.getItem() == Items.ENCHANTED_BOOK
            ? ((ItemEnchantmentsComponent)stack.get(DataComponentTypes.STORED_ENCHANTMENTS)).getEnchantments().size()
            : stack.getEnchantments().getSize();
      }

      return count;
   }

   private boolean shouldIgnoreCurrentScreenHandler(ClientPlayerEntity player) {
      if (this.mc.currentScreen == null) {
         return true;
      }

      if (player.currentScreenHandler == null) {
         return true;
      }

      ScreenHandler handler = player.currentScreenHandler;
      return handler instanceof PlayerScreenHandler
         ? !(Boolean)this.ownInventory.get()
         : !(handler instanceof AbstractFurnaceScreenHandler)
            && !(handler instanceof GenericContainerScreenHandler)
            && !(handler instanceof Generic3x3ContainerScreenHandler)
            && !(handler instanceof ShulkerBoxScreenHandler)
            && !(handler instanceof HopperScreenHandler)
            && !(handler instanceof HorseScreenHandler);
   }

   public boolean shouldHighlightSlot(ItemStack stack) {
      if (this.mc.player == null) {
         return false;
      }

      if (!stack.isEmpty() && !this.shouldIgnoreCurrentScreenHandler(this.mc.player)) {
         if (Utils.hasItems(stack)) {
            ItemStack[] stacks = new ItemStack[27];
            Utils.getItemsInContainerItem(stack, stacks);

            for (ItemStack s : stacks) {
               if (this.shouldHighlightSlot(s)) {
                  return true;
               }
            }
         }

         if (!((String)this.metadataSearch.get()).trim().isEmpty()) {
            ComponentMap metadata = stack.getComponents();
            String query = ((String)this.metadataSearch.get()).toLowerCase();
            if ((Boolean)this.splitQueries.get() && query.contains(",")) {
               String[] queries = query.split(",");
               if (metadata != null
                  && Arrays.stream(queries)
                     .anyMatch(
                        q -> metadata.toString().toLowerCase().contains(q.trim()) || metadata.toString().toLowerCase().contains(q.trim().replace(" ", "_"))
                     )) {
                  return true;
               }

               if (Arrays.stream(queries)
                  .anyMatch(
                     q -> stack.getName().getString().toLowerCase().contains(q.trim())
                        || stack.getItem().getDefaultStack().getName().getString().toLowerCase().contains(q.trim())
                  )) {
                  return true;
               }
            } else {
               if (metadata != null) {
                  if (metadata.toString().toLowerCase().contains(query.trim())) {
                     return true;
                  }

                  if (metadata.toString().toLowerCase().contains(query.trim().replace(" ", "_"))) {
                     return true;
                  }
               }

               if (stack.getName().getString().toLowerCase().contains(query.trim())) {
                  return true;
               }

               if (stack.getItem().getDefaultStack().getName().getString().toLowerCase().contains(query.trim())) {
                  return true;
               }
            }
         }

         if ((Boolean)this.lagRockets.get() && stack.contains(DataComponentTypes.FIREWORKS)) {
            FireworksComponent firework = (FireworksComponent)stack.get(DataComponentTypes.FIREWORKS);
            if (firework.explosions().size() == 7) {
               return true;
            }
         }

         if ((Boolean)this.illegalFish.get() && stack.isOf(Items.TROPICAL_FISH_BUCKET)) {
            NbtComponent nbtComponent = (NbtComponent)stack.getOrDefault(DataComponentTypes.BUCKET_ENTITY_DATA, NbtComponent.DEFAULT);
            if (!nbtComponent.isEmpty()) {
               Optional<Variant> optional = Variant.CODEC
                  .parse(NbtOps.INSTANCE, (NbtElement)nbtComponent.copyNbt().getCompound("BucketVariantTag").orElse(new NbtCompound()))
                  .result();
               if (optional.isPresent()) {
                  Variant variant = optional.get();
                  String string = "color.minecraft." + variant.baseColor();
                  String string2 = "color.minecraft." + variant.patternColor();
                  int i = TropicalFishEntity.COMMON_VARIANTS.indexOf(variant);
                  if (i == -1 && (string.contains("black") || string2.contains("black"))) {
                     return true;
                  }
               }
            }
         }

         if ((Boolean)this.writtenBooks.get() && stack.getItem() == Items.WRITTEN_BOOK) {
            return true;
         }

         if ((Boolean)this.petrifiedSlabs.get() && stack.getItem() == Items.PETRIFIED_OAK_SLAB) {
            return true;
         }

         if ((Boolean)this.renamedItems.get() && stack.contains(DataComponentTypes.CUSTOM_NAME)) {
            return true;
         }

         if ((Boolean)this.negativeDurability.get()
            && stack.isDamageable()
            && (Integer)stack.getOrDefault(DataComponentTypes.DAMAGE, stack.getDamage()) >= stack.getMaxDamage()) {
            return true;
         }

         if ((Boolean)this.illegalEnchants.get() && (stack.getItem() == Items.ENCHANTED_BOOK || stack.hasEnchantments())) {
            int enchantmentsCount = this.enchantmentsCount(stack);
            if (stack.getItem() == Items.SHEARS && Utils.hasEnchantment(stack, Enchantments.SILK_TOUCH)) {
               return enchantmentsCount == 1 || !(Boolean)this.onlySilkyShears.get();
            }

            if (stack.getItem() == Items.ENCHANTED_BOOK && (enchantmentsCount == 0 || enchantmentsCount > 7)) {
               return true;
            }

            boolean hasProtection = Utils.hasEnchantment(stack, Enchantments.PROTECTION);
            if (Utils.hasEnchantment(stack, Enchantments.FIRE_PROTECTION)) {
               if (hasProtection) {
                  return true;
               }

               hasProtection = true;
            }

            if (Utils.hasEnchantment(stack, Enchantments.BLAST_PROTECTION)) {
               if (hasProtection) {
                  return true;
               }

               hasProtection = true;
            }

            if (Utils.hasEnchantment(stack, Enchantments.PROJECTILE_PROTECTION) && hasProtection) {
               return true;
            }

            if (Utils.hasEnchantment(stack, Enchantments.INFINITY) && Utils.hasEnchantment(stack, Enchantments.MENDING)) {
               return enchantmentsCount == 2 || !(Boolean)this.onlyInfinityMending.get();
            }
         }

         return false;
      } else {
         return false;
      }
   }
}