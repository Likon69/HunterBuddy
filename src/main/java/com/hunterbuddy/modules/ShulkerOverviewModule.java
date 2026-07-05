package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.ShulkerDataParser;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;

public class ShulkerOverviewModule extends Module {
   private final WeakHashMap<ItemStack, ShulkerOverviewModule.CachedShulkerData> shulkerCache = new WeakHashMap<>();
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<Integer> iconSize = this.sgGeneral
      .add(
         new Builder().name("icon-size").description("Size of the item icon overlay.").defaultValue(12)
            .min(4)
            .max(16)
            .sliderMin(4)
            .sliderMax(16)
            .build()
      );
   public final Setting<ShulkerOverviewModule.IconPosition> iconPosition = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShulkerOverviewModule.IconPosition>()
                     .name("icon-position")
                  .description("Position of the item icon overlay.")
               .defaultValue(ShulkerOverviewModule.IconPosition.Center)
            .build()
      );
   public final Setting<String> multipleText = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                     .name("multiple-indicator")
                  .description("Text to show when shulker contains multiple item types.")
               .defaultValue("+")
            .build()
      );
   public final Setting<Integer> multipleSize = this.sgGeneral
      .add(
         new Builder().name("multiple-size").description("Size of the multiple indicator text.").defaultValue(8)
            .min(4)
            .max(16)
            .sliderMin(4)
            .sliderMax(16)
            .build()
      );
   public final Setting<Boolean> debugMode = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("debug-mode")
                  .description("Show debug information.")
               .defaultValue(false)
            .build()
      );

   public ShulkerOverviewModule() {
      super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "shulker-overview", "Overlays most common item icon on shulker boxes in inventory.");
   }

   public void renderShulkerOverlay(DrawContext context, int x, int y, ItemStack stack) {
      if (!stack.isEmpty()) {
         if (stack.getItem() instanceof BlockItem blockItem) {
            if (blockItem.getBlock() instanceof ShulkerBoxBlock) {
               ShulkerOverviewModule.CachedShulkerData cached = this.shulkerCache.get(stack);
               if (cached == null) {
                  Map<Item, Integer> itemCounts = ShulkerDataParser.parseShulkerContents(stack);
                  if (itemCounts.isEmpty()) {
                     return;
                  }

                  cached = new ShulkerOverviewModule.CachedShulkerData(itemCounts);
                  this.shulkerCache.put(stack, cached);
               }

               if (cached.mostCommonItem != null) {
                  Item item = cached.mostCommonItem;
                  boolean hasMultiple = cached.hasMultiple;
                  if ((Boolean)this.debugMode.get()) {
                     MinecraftClient mc = MinecraftClient.getInstance();
                     int count = cached.itemCounts.getOrDefault(item, 0);
                     String debug = String.format("Items: %d, Most: %s x%d", cached.itemCounts.size(), item.getName().getString(), count);
                     context.drawText(mc.textRenderer, debug, x, y - 10, 16777215, true);
                  }

                  int iconSize = (Integer)this.iconSize.get();
                  int iconX;
                  int iconY;
                  switch ((ShulkerOverviewModule.IconPosition)this.iconPosition.get()) {
                     case BottomLeft:
                        iconX = x;
                        iconY = y + 16 - iconSize;
                        break;
                     case TopRight:
                        iconX = x + 16 - iconSize;
                        iconY = y;
                        break;
                     case TopLeft:
                        iconX = x;
                        iconY = y;
                        break;
                     case Center:
                        iconX = x + (16 - iconSize) / 2;
                        iconY = y + (16 - iconSize) / 2;
                        break;
                     default:
                        iconX = x + 16 - iconSize;
                        iconY = y + 16 - iconSize;
                  }

                  context.getMatrices().pushMatrix();
                  if (iconSize == 16) {
                     context.drawItemWithoutEntity(new ItemStack(item), iconX, iconY);
                  } else {
                     float scale = iconSize / 16.0F;
                     context.getMatrices().translate(iconX, iconY);
                     context.getMatrices().scale(scale, scale);
                     context.drawItemWithoutEntity(new ItemStack(item), 0, 0);
                  }

                  context.getMatrices().popMatrix();
                  if (hasMultiple && !((String)this.multipleText.get()).isEmpty()) {
                     this.renderMultipleIndicator(context, x, y, (String)this.multipleText.get(), (Integer)this.multipleSize.get());
                  }
               }
            }
         }
      }
   }

   private void renderMultipleIndicator(DrawContext context, int slotX, int slotY, String text, int size) {
      MinecraftClient mc = MinecraftClient.getInstance();
      int textWidth = mc.textRenderer.getWidth(text);
      int textX = slotX + 16 - textWidth - 1;
      int textY = slotY + 1;
      context.drawText(mc.textRenderer, text, textX, textY, -256, true);
   }

   private static class CachedShulkerData {
      final Map<Item, Integer> itemCounts;
      final Item mostCommonItem;
      final boolean hasMultiple;

      CachedShulkerData(Map<Item, Integer> itemCounts) {
         this.itemCounts = itemCounts;
         this.hasMultiple = itemCounts.size() > 1;
         this.mostCommonItem = itemCounts.entrySet().stream().max(Entry.comparingByValue()).map(Entry::getKey).orElse(null);
      }
   }

   public enum IconPosition {
      BottomRight,
      BottomLeft,
      TopRight,
      TopLeft,
      Center;
   }
}
