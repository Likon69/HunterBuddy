package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;

public class DimensionCoords extends HudElement {
   public static final HudElementInfo<DimensionCoords> INFO = new HudElementInfo(
      HunterBuddyAddon.HUD_GROUP, "DimensionCoords", "Displays coordinates for both overworld and nether dimensions.", DimensionCoords::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> showTitle = this.sgGeneral
      .add(new Builder().name("show-title").description("Display the HUD title.").defaultValue(false).build());
   private final Setting<Boolean> showCurrentDim = this.sgGeneral
      .add(
         new Builder().name("show-current-dimension").description("Show current dimension name.").defaultValue(false).build()
      );
   private final Setting<Double> textScale = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("text-scale")
               .description("Scale of the text.")
            .defaultValue(1.0)
            .min(0.1)
            .sliderRange(0.1, 3.0)
            .build()
      );
   private final Setting<Boolean> textShadow = this.sgGeneral
      .add(new Builder().name("text-shadow").description("Render shadow behind the text.").defaultValue(true).build());
   private final Setting<SettingColor> titleColor = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("title-color")
               .description("Color for the title text.")
            .defaultValue(new SettingColor(255, 255, 255, 255)).build()
      );
   private final Setting<SettingColor> overworldColor = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("overworld-color")
               .description("Color for overworld coordinates.")
            .defaultValue(new SettingColor(0, 255, 0, 255)).build()
      );
   private final Setting<SettingColor> netherColor = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("nether-color")
               .description("Color for nether coordinates.")
            .defaultValue(new SettingColor(255, 0, 0, 255)).build()
      );
   private final Setting<SettingColor> endColor = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("end-color")
               .description("Color for end coordinates.")
            .defaultValue(new SettingColor(255, 0, 255, 255)).build()
      );
   private final Setting<Boolean> showLabels = this.sgGeneral
      .add(
         new Builder().name("show-labels").description("Show dimension labels (e.g. 'Overworld:', 'Nether:').")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> removeCommas = this.sgGeneral
      .add(new Builder().name("remove-commas").description("Remove commas from coordinates.").defaultValue(false).build());
   private final Setting<Boolean> horizontalLayout = this.sgGeneral
      .add(
         new Builder().name("horizontal-layout").description("Display coordinates horizontally next to each other.")
               .defaultValue(false)
            .build()
      );

   public DimensionCoords() {
      super(INFO);
   }

   public void render(HudRenderer renderer) {
      if (MeteorClient.mc.world != null && MeteorClient.mc.player != null) {
         BlockPos playerPos = MeteorClient.mc.player.getBlockPos();
         Identifier dimensionId = MeteorClient.mc.world.getRegistryKey().getValue();
         double curX = this.x;
         double curY = this.y;
         double maxWidth = 0.0;
         double height = 0.0;
         double textHeight = renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         double spacing = 2.0;
         if ((Boolean)this.showTitle.get()) {
            String title = "Dimension Coords";
            double titleWidth = renderer.textWidth(title, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            renderer.text(title, curX, curY, (Color)this.titleColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, titleWidth);
         }

         if ((Boolean)this.showCurrentDim.get()) {
            String dimName = this.getDimensionName(dimensionId);
            String dimText = "Current: " + dimName;
            double dimWidth = renderer.textWidth(dimText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            renderer.text(dimText, curX, curY, (Color)this.titleColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, dimWidth);
         }

         String coordFormat = this.removeCommas.get() ? "%d %d %d" : "%d, %d, %d";
         if (this.isOverworld(dimensionId)) {
            String overworldLabel = this.showLabels.get() ? "Overworld: " : "";
            String netherLabel = this.showLabels.get() ? "Nether: " : "";
            String overworldText = overworldLabel + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
            String netherText = netherLabel + String.format(coordFormat, playerPos.getX() / 8, playerPos.getY(), playerPos.getZ() / 8);
            if ((Boolean)this.horizontalLayout.get()) {
               double overworldWidth = renderer.textWidth(overworldText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               renderer.text(overworldText, curX, curY, (Color)this.overworldColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               curX += overworldWidth + spacing * 3.0;
               renderer.text(netherText, curX, curY, (Color)this.netherColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               double netherWidth = renderer.textWidth(netherText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               maxWidth = Math.max(maxWidth, overworldWidth + netherWidth + spacing * 3.0);
               height += textHeight;
            } else {
               double overworldWidth = renderer.textWidth(overworldText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               double netherWidth = renderer.textWidth(netherText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               renderer.text(overworldText, curX, curY, (Color)this.overworldColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               curY += textHeight + spacing;
               height += textHeight + spacing;
               maxWidth = Math.max(maxWidth, overworldWidth);
               renderer.text(netherText, curX, curY, (Color)this.netherColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               curY += textHeight + spacing;
               height += textHeight + spacing;
               maxWidth = Math.max(maxWidth, netherWidth);
            }
         } else if (this.isNether(dimensionId)) {
            String netherLabel = this.showLabels.get() ? "Nether: " : "";
            String overworldLabel = this.showLabels.get() ? "Overworld: " : "";
            String netherText = netherLabel + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
            String overworldText = overworldLabel
               + String.format(coordFormat, playerPos.getX() * 8, playerPos.getY(), playerPos.getZ() * 8);
            if ((Boolean)this.horizontalLayout.get()) {
               double netherWidth = renderer.textWidth(netherText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               renderer.text(netherText, curX, curY, (Color)this.netherColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               curX += netherWidth + spacing * 3.0;
               renderer.text(overworldText, curX, curY, (Color)this.overworldColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               double overworldWidth = renderer.textWidth(overworldText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               maxWidth = Math.max(maxWidth, netherWidth + overworldWidth + spacing * 3.0);
               height += textHeight;
            } else {
               double netherWidth = renderer.textWidth(netherText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               double overworldWidth = renderer.textWidth(overworldText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               renderer.text(netherText, curX, curY, (Color)this.netherColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               curY += textHeight + spacing;
               height += textHeight + spacing;
               maxWidth = Math.max(maxWidth, netherWidth);
               renderer.text(overworldText, curX, curY, (Color)this.overworldColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
               curY += textHeight + spacing;
               height += textHeight + spacing;
               maxWidth = Math.max(maxWidth, overworldWidth);
            }
         } else if (this.isEnd(dimensionId)) {
            String endLabel = this.showLabels.get() ? "The End: " : "";
            String endText = endLabel + String.format(coordFormat, playerPos.getX(), playerPos.getY(), playerPos.getZ());
            double endWidth = renderer.textWidth(endText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            renderer.text(endText, curX, curY, (Color)this.endColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, endWidth);
         }

         this.setSize(maxWidth, height > 0.0 ? height - spacing : 0.0);
      } else {
         if (this.isInEditor()) {
            renderer.text("Dimension Coords", this.x, this.y, (Color)this.titleColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            this.setSize(
               renderer.textWidth("Dimension Coords", (Boolean)this.textShadow.get(), (Double)this.textScale.get()),
               renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get())
            );
         }
      }
   }

   private boolean isOverworld(Identifier dimensionId) {
      return dimensionId.equals(Identifier.of("minecraft:overworld"));
   }

   private boolean isNether(Identifier dimensionId) {
      return dimensionId.equals(Identifier.of("minecraft:the_nether"));
   }

   private boolean isEnd(Identifier dimensionId) {
      return dimensionId.equals(Identifier.of("minecraft:the_end"));
   }

   private String getDimensionName(Identifier dimensionId) {
      if (this.isOverworld(dimensionId)) {
         return "Overworld";
      } else if (this.isNether(dimensionId)) {
         return "Nether";
      } else {
         return this.isEnd(dimensionId) ? "The End" : dimensionId.getPath();
      }
   }
}
