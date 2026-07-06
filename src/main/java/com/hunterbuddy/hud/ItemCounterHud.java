package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.ItemListSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ItemCounterHud extends HudElement {
   public static final HudElementInfo<ItemCounterHud> INFO = new HudElementInfo(
      HunterBuddyAddon.HUD_GROUP, "item-counter", "Displays selected items and their inventory counts.", ItemCounterHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDisplay = this.settings.createGroup("Display");
   private final Setting<List<Item>> items = this.sgGeneral
      .add(new Builder().name("items").description("Items to track and display in the HUD.").build());
   private final Setting<Boolean> showTitle = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-title")
                  .description("Display the HUD title.")
               .defaultValue(false)
            .build()
      );
   private final Setting<String> titleText = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                        .name("title-text")
                     .description("Custom title text.")
                  .defaultValue("Item Counter")
               .visible(this.showTitle::get)
            .build()
      );
   private final Setting<Boolean> showZero = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-zero")
                  .description("Show items with zero count.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> showTotal = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-total")
                  .description("Show total count of all tracked items.")
               .defaultValue(false)
            .build()
      );
   private final Setting<ItemCounterHud.Layout> layout = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ItemCounterHud.Layout>()
                     .name("layout")
                  .description("Layout of the displayed items.")
               .defaultValue(ItemCounterHud.Layout.Vertical)
            .build()
      );
   private final Setting<Double> itemScale = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("item-scale")
               .description("Scale of the item icons.")
            .defaultValue(1.0)
            .min(0.1)
            .max(3.0)
            .sliderRange(0.1, 3.0)
            .build()
      );
   private final Setting<Double> textScale = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("text-scale")
               .description("Scale of the count text.")
            .defaultValue(1.0)
            .min(0.1)
            .max(3.0)
            .sliderRange(0.1, 3.0)
            .build()
      );
   private final Setting<SettingColor> textColor = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("text-color")
               .description("Color of the count text.")
            .defaultValue(new SettingColor(255, 255, 255, 255)).build()
      );
   private final Setting<SettingColor> zeroColor = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                     .name("zero-color")
                  .description("Color for items with zero count.")
               .defaultValue(new SettingColor(128, 128, 128, 255)).visible(this.showZero::get)
            .build()
      );
   private final Setting<SettingColor> lowCountColor = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("low-count-color")
               .description("Color for items with low count.")
            .defaultValue(new SettingColor(255, 100, 100, 255)).build()
      );
   private final Setting<Integer> lowCountThreshold = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("low-count-threshold")
                  .description("Threshold for low count warning.")
               .defaultValue(10)
            .min(1)
            .max(100)
            .sliderRange(1, 100)
            .build()
      );
   private final Setting<Boolean> textShadow = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("text-shadow")
                  .description("Render shadow behind the text.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> showStackCount = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-stack-count")
                  .description("Show count in stacks (e.g., 2.5 stacks).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> showItemName = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-item-name")
                  .description("Show item name next to count.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Integer> maxItemsPerRow = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("max-items-per-row")
                     .description("Maximum items per row in horizontal layout.")
                  .defaultValue(8)
               .min(1)
               .max(20)
               .sliderRange(1, 20)
               .visible(() -> this.layout.get() == ItemCounterHud.Layout.Horizontal)
            .build()
      );

   public ItemCounterHud() {
      super(INFO);
   }

   public void render(HudRenderer renderer) {
      double curX = this.x;
      double curY = this.y;
      double startX = this.x;
      double width = 0.0;
      double height = 0.0;
      if (this.isInEditor()) {
         String preview = (String)this.titleText.get();
         renderer.text(preview, curX, curY, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         curY += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get()) + 2.0;
         ItemStack diamond = new ItemStack(Items.DIAMOND);
         double editorItemX = curX;
         double editorItemY = curY;
         float editorItemScale = ((Double)this.itemScale.get()).floatValue();
         renderer.post(() -> renderer.item(diamond, (int)editorItemX, (int)editorItemY, editorItemScale, true));
         renderer.text(
            "64",
            curX + 16.0 * (Double)this.itemScale.get() + 2.0,
            curY + (8.0 * (Double)this.itemScale.get() - renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get()) / 2.0),
            (Color)this.textColor.get(),
            (Boolean)this.textShadow.get(),
            (Double)this.textScale.get()
         );
         this.setSize(
            Math.max(
               renderer.textWidth(preview, (Boolean)this.textShadow.get(), (Double)this.textScale.get()),
               16.0 * (Double)this.itemScale.get() + renderer.textWidth("64", (Boolean)this.textShadow.get(), (Double)this.textScale.get()) + 2.0
            ),
            renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get()) + 2.0 + 16.0 * (Double)this.itemScale.get()
         );
      } else if (MeteorClient.mc.player != null && MeteorClient.mc.world != null) {
         if ((Boolean)this.showTitle.get()) {
            String title = (String)this.titleText.get();
            renderer.text(title, curX, curY, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            curY += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get()) + 2.0;
            height += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get()) + 2.0;
            width = Math.max(width, renderer.textWidth(title, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
         }

         double itemSize = 16.0 * (Double)this.itemScale.get();
         double textHeight = renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         double spacing = 2.0;
         int totalCount = 0;
         int itemsInRow = 0;

         for (Item item : this.items.get()) {
            ItemStack stack = new ItemStack(item);
            int count = InvUtils.find(new Item[]{item}).count();
            totalCount += count;
            if (count != 0 || (Boolean)this.showZero.get()) {
               SettingColor countColor = (SettingColor)this.textColor.get();
               if (count == 0) {
                  countColor = (SettingColor)this.zeroColor.get();
               } else if (count < (Integer)this.lowCountThreshold.get()) {
                  countColor = (SettingColor)this.lowCountColor.get();
               }

               double itemX = curX;
               double itemY = curY;
               float iScale = ((Double)this.itemScale.get()).floatValue();
               renderer.post(() -> renderer.item(stack, (int)itemX, (int)itemY, iScale, true));
               String countText;
               if ((Boolean)this.showStackCount.get() && stack.getMaxCount() > 1) {
                  double stacks = (double)count / stack.getMaxCount();
                  countText = String.format("%.1f", stacks);
               } else {
                  countText = String.valueOf(count);
               }

               if ((Boolean)this.showItemName.get()) {
                  String itemName = item.getName().getString();
                  if (itemName.length() > 10) {
                     itemName = itemName.substring(0, 8) + "..";
                  }

                  countText = countText + " " + itemName;
               }

               double textX = curX + itemSize + spacing;
               double textY = curY + (itemSize / 2.0 - textHeight / 2.0);
               renderer.text(countText, textX, textY, countColor, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               double itemWidth = itemSize + spacing + renderer.textWidth(countText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               if (this.layout.get() == ItemCounterHud.Layout.Horizontal) {
                  curX += itemWidth + spacing;
                  width += itemWidth + spacing;
                  height = Math.max(height, itemSize);
               } else if (this.layout.get() == ItemCounterHud.Layout.Grid) {
                  if (++itemsInRow >= (Integer)this.maxItemsPerRow.get()) {
                     curX = startX;
                     curY += itemSize + spacing;
                     height += itemSize + spacing;
                     itemsInRow = 0;
                  } else {
                     curX += itemWidth + spacing;
                     width = Math.max(width, curX - startX);
                  }

                  if (itemsInRow == 0) {
                     height = Math.max(height, itemSize);
                  }
               } else {
                  curY += itemSize + spacing;
                  height += itemSize + spacing;
                  width = Math.max(width, itemWidth);
               }
            }
         }

         if ((Boolean)this.showTotal.get() && totalCount > 0) {
            if (this.layout.get() != ItemCounterHud.Layout.Vertical) {
               curY += itemSize + spacing;
            }

            String totalText = "Total: " + totalCount;
            renderer.text(totalText, startX, curY, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            height += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get()) + spacing;
            width = Math.max(width, renderer.textWidth(totalText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
         }

         this.setSize(width, height);
      } else {
         this.setSize(0.0, 0.0);
      }
   }

   public enum Layout {
      Vertical,
      Horizontal,
      Grid;
   }
}
