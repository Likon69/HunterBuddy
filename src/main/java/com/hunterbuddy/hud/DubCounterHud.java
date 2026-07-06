package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.DyeColor;
import net.minecraft.block.ChestBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.ChestType;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.block.entity.SmokerBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.DecoratedPotBlockEntity;

public class DubCounterHud extends HudElement {
   public static final HudElementInfo<DubCounterHud> INFO = new HudElementInfo(
      HunterBuddyAddon.HUD_GROUP, "DubCounter", "Displays count of all containers in render distance for 2b2t looting.", DubCounterHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDisplay = this.settings.createGroup("Display");
   private final SettingGroup sgContainers = this.settings.createGroup("Containers");
   private final Setting<String> titleText = this.sgGeneral
      .add(new Builder().name("title-text").description("Custom title text for the HUD.").defaultValue("Dub Counter").build());
   private final Setting<Boolean> showTitle = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-title")
                  .description("Display the HUD title.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> showChestBreakdown = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-chest-breakdown")
                  .description("Show single and double chest counts (S:X D:X).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> showShulkerBreakdown = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-shulker-breakdown")
                  .description("Display shulker boxes broken down by color.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> sortShulkersByCount = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("sort-shulkers-by-count")
                     .description("Sort shulker colors by count (highest first).")
                  .defaultValue(true)
               .visible(this.showShulkerBreakdown::get)
            .build()
      );
   private final Setting<Boolean> showTotalValue = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-total-value")
                  .description("Show estimated total storage slots.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> compactMode = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("compact-mode")
                  .description("Show counts in a more compact format.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> showZeroCounts = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-zero-counts")
                  .description("Always show toggled containers even when count is 0.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> countChests = this.sgContainers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chests")
                  .description("Count regular and trapped chests.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> countBarrels = this.sgContainers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("barrels")
                  .description("Count barrels.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> countShulkers = this.sgContainers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("shulker-boxes")
                  .description("Count shulker boxes.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> countEnderChests = this.sgContainers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("ender-chests")
                  .description("Count ender chests.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> countHoppers = this.sgContainers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("hoppers")
                  .description("Count hoppers (valuable for farms).")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> countDroppers = this.sgContainers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("droppers")
                  .description("Count droppers.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> countDispensers = this.sgContainers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("dispensers")
                  .description("Count dispensers.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> countFurnaces = this.sgContainers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("furnaces")
                  .description("Count furnaces (includes blast furnaces and smokers).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> countBrewingStands = this.sgContainers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("brewing-stands")
                  .description("Count brewing stands.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> countLecterns = this.sgContainers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("lecterns")
                  .description("Count lecterns (book holders).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> countCrafters = this.sgContainers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("crafters")
                  .description("Count crafters (auto-crafting blocks).")
               .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> countDecoratedPots = this.sgContainers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("decorated-pots")
                  .description("Count decorated pots.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Double> textScale = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("text-scale")
               .description("Scale of the text.")
            .defaultValue(1.0)
            .min(0.5)
            .max(3.0)
            .sliderRange(0.5, 3.0)
            .build()
      );
   private final Setting<SettingColor> titleColor = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("title-color")
               .description("Color of the title text.")
            .defaultValue(new SettingColor(255, 255, 255, 255)).build()
      );
   private final Setting<SettingColor> textColor = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("text-color")
               .description("Color of the count text.")
            .defaultValue(new SettingColor(200, 200, 200, 255)).build()
      );
   private final Setting<SettingColor> shulkerTextColor = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("shulker-text-color")
               .description("Color of the shulker count text.")
            .defaultValue(new SettingColor(255, 200, 100, 255)).build()
      );
   private final Setting<SettingColor> valueTextColor = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("value-text-color")
               .description("Color of the value text.")
            .defaultValue(new SettingColor(100, 255, 100, 255)).build()
      );
   private final Setting<Boolean> textShadow = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("text-shadow")
                  .description("Render shadow behind the text.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> rainbowTitle = this.sgDisplay
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rainbow-title")
                  .description("Rainbow colored title.")
               .defaultValue(false)
            .build()
      );

   public DubCounterHud() {
      super(INFO);
   }

   public void render(HudRenderer renderer) {
      if (MeteorClient.mc.world != null && MeteorClient.mc.player != null) {
         DubCounterHud.ContainerCounts counts = this.countContainers();
         this.renderCounts(renderer, counts);
      } else {
         if (this.isInEditor()) {
            this.renderPlaceholder(renderer);
         }
      }
   }

   private void renderPlaceholder(HudRenderer renderer) {
      double y = this.y;
      double maxWidth = 0.0;
      if ((Boolean)this.showTitle.get()) {
         renderer.text((String)this.titleText.get(), this.x, y, (Color)this.titleColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth((String)this.titleText.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get()) + 2.0;
      }

      renderer.text("Dubs: 12.5", this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
      maxWidth = Math.max(maxWidth, renderer.textWidth("Dubs: 12.5", (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
      renderer.text("Barrels: 24", this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
      maxWidth = Math.max(maxWidth, renderer.textWidth("Barrels: 24", (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
      renderer.text("Shulkers: 37", this.x, y, (Color)this.shulkerTextColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
      maxWidth = Math.max(maxWidth, renderer.textWidth("Shulkers: 37", (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
      if ((Boolean)this.showTotalValue.get()) {
         renderer.text("Storage: ~2145 slots", this.x, y, (Color)this.valueTextColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth("Storage: ~2145 slots", (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
      }

      this.setSize(maxWidth, y - this.y);
   }

   private void renderCounts(HudRenderer renderer, DubCounterHud.ContainerCounts counts) {
      double y = this.y;
      double maxWidth = 0.0;
      if ((Boolean)this.showTitle.get()) {
         SettingColor color = this.rainbowTitle.get() ? this.getRainbowColor() : (SettingColor)this.titleColor.get();
         renderer.text((String)this.titleText.get(), this.x, y, color, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get()) + 2.0;
         maxWidth = Math.max(maxWidth, renderer.textWidth((String)this.titleText.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      if ((Boolean)this.countChests.get() && (counts.totalDubs > 0.0 || (Boolean)this.showZeroCounts.get())) {
         String dubText;
         if ((Boolean)this.compactMode.get()) {
            dubText = String.format("D: %.1f", counts.totalDubs);
         } else if ((Boolean)this.showChestBreakdown.get()) {
            dubText = String.format("Dubs: %.1f (S:%d D:%d)", counts.totalDubs, counts.singleChests, counts.doubleChests);
         } else {
            dubText = String.format("Dubs: %.1f", counts.totalDubs);
         }

         renderer.text(dubText, this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(dubText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      if ((Boolean)this.countBarrels.get() && (counts.barrelCount > 0 || (Boolean)this.showZeroCounts.get())) {
         String barrelText = this.compactMode.get() ? String.format("B: %d", counts.barrelCount) : String.format("Barrels: %d", counts.barrelCount);
         renderer.text(barrelText, this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(barrelText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      if ((Boolean)this.countShulkers.get() && (counts.totalShulkers > 0 || (Boolean)this.showZeroCounts.get())) {
         String shulkerTitle = this.compactMode.get() ? String.format("S: %d", counts.totalShulkers) : String.format("Shulkers: %d", counts.totalShulkers);
         renderer.text(shulkerTitle, this.x, y, (Color)this.shulkerTextColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(shulkerTitle, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
         if ((Boolean)this.showShulkerBreakdown.get()) {
            Map<String, Integer> shulkersToShow = this.sortShulkersByCount.get() ? this.sortByValue(counts.shulkersByColor) : counts.shulkersByColor;

            for (Entry<String, Integer> entry : shulkersToShow.entrySet()) {
               if (entry.getValue() > 0 || (Boolean)this.showZeroCounts.get()) {
                  String colorText = this.compactMode.get()
                     ? String.format("  %s: %d", this.getShortColorName(entry.getKey()), entry.getValue())
                     : String.format("  %s: %d", entry.getKey(), entry.getValue());
                  SettingColor color = this.getShulkerColor(entry.getKey());
                  renderer.text(colorText, this.x, y, color, (Boolean)this.textShadow.get(), (Double)this.textScale.get() * 0.9);
                  y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get() * 0.9);
                  maxWidth = Math.max(maxWidth, renderer.textWidth(colorText, (Boolean)this.textShadow.get(), (Double)this.textScale.get() * 0.9));
               }
            }
         }
      }

      if ((Boolean)this.countEnderChests.get() && (counts.enderChestCount > 0 || (Boolean)this.showZeroCounts.get())) {
         String enderText = this.compactMode.get() ? String.format("E: %d", counts.enderChestCount) : String.format("Ender Chests: %d", counts.enderChestCount);
         renderer.text(enderText, this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(enderText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      if ((Boolean)this.countHoppers.get() && (counts.hopperCount > 0 || (Boolean)this.showZeroCounts.get())) {
         String hopperText = this.compactMode.get() ? String.format("H: %d", counts.hopperCount) : String.format("Hoppers: %d", counts.hopperCount);
         renderer.text(hopperText, this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(hopperText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      if ((Boolean)this.countDroppers.get() && (counts.dropperCount > 0 || (Boolean)this.showZeroCounts.get())) {
         String dropperText = this.compactMode.get() ? String.format("Dr: %d", counts.dropperCount) : String.format("Droppers: %d", counts.dropperCount);
         renderer.text(dropperText, this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(dropperText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      if ((Boolean)this.countDispensers.get() && (counts.dispenserCount > 0 || (Boolean)this.showZeroCounts.get())) {
         String dispenserText = this.compactMode.get()
            ? String.format("Di: %d", counts.dispenserCount)
            : String.format("Dispensers: %d", counts.dispenserCount);
         renderer.text(dispenserText, this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(dispenserText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      if ((Boolean)this.countFurnaces.get() && (counts.furnaceCount > 0 || (Boolean)this.showZeroCounts.get())) {
         String furnaceText = this.compactMode.get() ? String.format("F: %d", counts.furnaceCount) : String.format("Furnaces: %d", counts.furnaceCount);
         renderer.text(furnaceText, this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(furnaceText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      if ((Boolean)this.countBrewingStands.get() && (counts.brewingStandCount > 0 || (Boolean)this.showZeroCounts.get())) {
         String brewText = this.compactMode.get()
            ? String.format("BS: %d", counts.brewingStandCount)
            : String.format("Brewing Stands: %d", counts.brewingStandCount);
         renderer.text(brewText, this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(brewText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      if ((Boolean)this.countLecterns.get() && (counts.lecternCount > 0 || (Boolean)this.showZeroCounts.get())) {
         String lecternText = this.compactMode.get() ? String.format("L: %d", counts.lecternCount) : String.format("Lecterns: %d", counts.lecternCount);
         renderer.text(lecternText, this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(lecternText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      if ((Boolean)this.countCrafters.get() && (counts.crafterCount > 0 || (Boolean)this.showZeroCounts.get())) {
         String crafterText = this.compactMode.get() ? String.format("Cr: %d", counts.crafterCount) : String.format("Crafters: %d", counts.crafterCount);
         renderer.text(crafterText, this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(crafterText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      if ((Boolean)this.countDecoratedPots.get() && (counts.decoratedPotCount > 0 || (Boolean)this.showZeroCounts.get())) {
         String potText = this.compactMode.get()
            ? String.format("DP: %d", counts.decoratedPotCount)
            : String.format("Decorated Pots: %d", counts.decoratedPotCount);
         renderer.text(potText, this.x, y, (Color)this.textColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(potText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      if ((Boolean)this.showTotalValue.get() && counts.getTotalSlots() > 0) {
         y += 2.0;
         String valueText = this.compactMode.get()
            ? String.format("Slots: %d", counts.getTotalSlots())
            : String.format("Storage: ~%d slots", counts.getTotalSlots());
         renderer.text(valueText, this.x, y, (Color)this.valueTextColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         y += renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         maxWidth = Math.max(maxWidth, renderer.textWidth(valueText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()));
      }

      this.setSize(maxWidth, y - this.y);
   }

   private Map<String, Integer> sortByValue(Map<String, Integer> map) {
      Map<String, Integer> sorted = new TreeMap<>((a, b) -> {
         int comp = map.get(b).compareTo(map.get(a));
         return comp != 0 ? comp : a.compareTo(b);
      });
      sorted.putAll(map);
      return sorted;
   }

   private SettingColor getRainbowColor() {
      double time = System.currentTimeMillis() / 1000.0;
      int r = (int)((Math.sin(time) + 1.0) * 127.5);
      int g = (int)((Math.sin(time + 2.094) + 1.0) * 127.5);
      int b = (int)((Math.sin(time + 4.189) + 1.0) * 127.5);
      return new SettingColor(r, g, b, 255);
   }

   private SettingColor getShulkerColor(String colorName) {
      return switch (colorName) {
         case "White" -> new SettingColor(255, 255, 255, 255);
         case "Orange" -> new SettingColor(255, 165, 0, 255);
         case "Magenta" -> new SettingColor(255, 0, 255, 255);
         case "Light Blue" -> new SettingColor(173, 216, 230, 255);
         case "Yellow" -> new SettingColor(255, 255, 0, 255);
         case "Lime" -> new SettingColor(50, 205, 50, 255);
         case "Pink" -> new SettingColor(255, 192, 203, 255);
         case "Gray" -> new SettingColor(128, 128, 128, 255);
         case "Light Gray" -> new SettingColor(211, 211, 211, 255);
         case "Cyan" -> new SettingColor(0, 255, 255, 255);
         case "Purple" -> new SettingColor(128, 0, 128, 255);
         case "Blue" -> new SettingColor(0, 0, 255, 255);
         case "Brown" -> new SettingColor(139, 69, 19, 255);
         case "Green" -> new SettingColor(0, 128, 0, 255);
         case "Red" -> new SettingColor(255, 0, 0, 255);
         case "Black" -> new SettingColor(50, 50, 50, 255);
         case "Undyed" -> new SettingColor(150, 100, 75, 255);
         default -> (SettingColor)this.textColor.get();
      };
   }

   private String getShortColorName(String fullName) {
      return switch (fullName) {
         case "White" -> "W";
         case "Orange" -> "O";
         case "Magenta" -> "M";
         case "Light Blue" -> "LB";
         case "Yellow" -> "Y";
         case "Lime" -> "Li";
         case "Pink" -> "Pi";
         case "Gray" -> "Gr";
         case "Light Gray" -> "LG";
         case "Cyan" -> "C";
         case "Purple" -> "Pu";
         case "Blue" -> "B";
         case "Brown" -> "Br";
         case "Green" -> "Gn";
         case "Red" -> "R";
         case "Black" -> "Bl";
         case "Undyed" -> "U";
         default -> fullName.substring(0, Math.min(3, fullName.length()));
      };
   }

   private DubCounterHud.ContainerCounts countContainers() {
      DubCounterHud.ContainerCounts counts = new DubCounterHud.ContainerCounts();
      if (MeteorClient.mc.world != null && MeteorClient.mc.player != null) {
         int renderDistance = (Integer)MeteorClient.mc.options.getViewDistance().getValue();
         int playerChunkX = MeteorClient.mc.player.getChunkPos().x;
         int playerChunkZ = MeteorClient.mc.player.getChunkPos().z;

         for (int cx = playerChunkX - renderDistance; cx <= playerChunkX + renderDistance; cx++) {
            for (int cz = playerChunkZ - renderDistance; cz <= playerChunkZ + renderDistance; cz++) {
               WorldChunk chunk = MeteorClient.mc.world.getChunk(cx, cz);
               if (chunk != null) {
                  for (BlockPos pos : chunk.getBlockEntityPositions()) {
                     BlockEntity blockEntity = chunk.getBlockEntity(pos);
                     if (blockEntity != null) {
                        if ((Boolean)this.countChests.get() && (blockEntity instanceof ChestBlockEntity || blockEntity instanceof TrappedChestBlockEntity)) {
                           BlockState blockState = blockEntity.getCachedState();
                           if (blockState.getBlock() instanceof ChestBlock && blockState.contains(ChestBlock.CHEST_TYPE)) {
                              ChestType type = (ChestType)blockState.get(ChestBlock.CHEST_TYPE);
                              if (type == ChestType.SINGLE) {
                                 counts.singleChests++;
                              } else if (type == ChestType.LEFT) {
                                 counts.doubleChests++;
                              }
                           }
                        }

                        if ((Boolean)this.countBarrels.get() && blockEntity instanceof BarrelBlockEntity) {
                           counts.barrelCount++;
                        }

                        if ((Boolean)this.countShulkers.get() && blockEntity instanceof ShulkerBoxBlockEntity shulker) {
                           counts.totalShulkers++;
                           DyeColor color = shulker.getColor();
                           String colorName = color != null ? this.getColorName(color) : "Undyed";
                           counts.shulkersByColor.merge(colorName, 1, Integer::sum);
                        }

                        if ((Boolean)this.countEnderChests.get() && blockEntity instanceof EnderChestBlockEntity) {
                           counts.enderChestCount++;
                        }

                        if ((Boolean)this.countHoppers.get() && blockEntity instanceof HopperBlockEntity) {
                           counts.hopperCount++;
                        }

                        if ((Boolean)this.countDroppers.get() && blockEntity instanceof DropperBlockEntity) {
                           counts.dropperCount++;
                        }

                        if ((Boolean)this.countDispensers.get() && blockEntity instanceof DispenserBlockEntity && !(blockEntity instanceof DropperBlockEntity)) {
                           counts.dispenserCount++;
                        }

                        if ((Boolean)this.countFurnaces.get()
                           && (blockEntity instanceof FurnaceBlockEntity || blockEntity instanceof BlastFurnaceBlockEntity || blockEntity instanceof SmokerBlockEntity)) {
                           counts.furnaceCount++;
                        }

                        if ((Boolean)this.countBrewingStands.get() && blockEntity instanceof BrewingStandBlockEntity) {
                           counts.brewingStandCount++;
                        }

                        if ((Boolean)this.countLecterns.get() && blockEntity instanceof LecternBlockEntity) {
                           counts.lecternCount++;
                        }

                        if ((Boolean)this.countCrafters.get()) {
                           String className = blockEntity.getClass().getSimpleName();
                           if (className.equals("CrafterBlockEntity")) {
                              counts.crafterCount++;
                           }
                        }

                        if ((Boolean)this.countDecoratedPots.get() && blockEntity instanceof DecoratedPotBlockEntity) {
                           counts.decoratedPotCount++;
                        }
                     }
                  }
               }
            }
         }

         counts.totalDubs = counts.doubleChests + counts.singleChests * 0.5;
         return counts;
      } else {
         return counts;
      }
   }

   private String getColorName(DyeColor color) {
      return switch (color) {
         case WHITE -> "White";
         case ORANGE -> "Orange";
         case MAGENTA -> "Magenta";
         case LIGHT_BLUE -> "Light Blue";
         case YELLOW -> "Yellow";
         case LIME -> "Lime";
         case PINK -> "Pink";
         case GRAY -> "Gray";
         case LIGHT_GRAY -> "Light Gray";
         case CYAN -> "Cyan";
         case PURPLE -> "Purple";
         case BLUE -> "Blue";
         case BROWN -> "Brown";
         case GREEN -> "Green";
         case RED -> "Red";
         case BLACK -> "Black";
         default -> throw new MatchException(null, null);
      };
   }

   private static class ContainerCounts {
      int singleChests = 0;
      int doubleChests = 0;
      double totalDubs = 0.0;
      int barrelCount = 0;
      int hopperCount = 0;
      int dropperCount = 0;
      int dispenserCount = 0;
      int enderChestCount = 0;
      int furnaceCount = 0;
      int brewingStandCount = 0;
      int lecternCount = 0;
      int crafterCount = 0;
      int decoratedPotCount = 0;
      int totalShulkers = 0;
      Map<String, Integer> shulkersByColor = new HashMap<>();

      int getTotalSlots() {
         int slots = 0;
         slots += this.singleChests * 27;
         slots += this.doubleChests * 54;
         slots += this.barrelCount * 27;
         slots += this.hopperCount * 5;
         slots += this.dropperCount * 9;
         slots += this.dispenserCount * 9;
         slots += this.totalShulkers * 27;
         slots += this.enderChestCount * 27;
         slots += this.furnaceCount * 3;
         slots += this.brewingStandCount * 5;
         slots += this.lecternCount * 1;
         slots += this.crafterCount * 9;
         return slots + this.decoratedPotCount * 1;
      }
   }
}
