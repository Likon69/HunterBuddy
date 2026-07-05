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
import net.minecraft.entity.player.PlayerEntity;

public class SpeedKMH extends HudElement {
   public static final HudElementInfo<SpeedKMH> INFO = new HudElementInfo(HunterBuddyAddon.HUD_GROUP, "SpeedKMH", "Displays movement speed in KM/H.", SpeedKMH::new);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> showTitle = this.sgGeneral
      .add(new Builder().name("show-title").description("Display the HUD title.").defaultValue(true).build());
   private final Setting<Boolean> showHorizontalOnly = this.sgGeneral
      .add(
         new Builder().name("horizontal-only").description("Only calculate horizontal speed (ignore Y movement).")
               .defaultValue(true)
            .build()
      );
   private final Setting<Integer> decimalPlaces = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("decimal-places")
                  .description("Number of decimal places to show.")
               .defaultValue(1)
            .min(0)
            .max(3)
            .sliderRange(0, 3)
            .build()
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
   private final Setting<SettingColor> speedColor = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("speed-color")
               .description("Color for the speed text.")
            .defaultValue(new SettingColor(0, 255, 255, 255)).build()
      );
   private double currentSpeed = 0.0;

   public SpeedKMH() {
      super(INFO);
   }

   public void render(HudRenderer renderer) {
      if (MeteorClient.mc.world != null && MeteorClient.mc.player != null) {
         this.updateSpeed();
         double curX = this.x;
         double curY = this.y;
         double maxWidth = 0.0;
         double height = 0.0;
         double textHeight = renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get());
         double spacing = 2.0;
         if ((Boolean)this.showTitle.get()) {
            String title = "Speed";
            double titleWidth = renderer.textWidth(title, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            renderer.text(title, curX, curY, (Color)this.titleColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            curY += textHeight + spacing;
            height += textHeight + spacing;
            maxWidth = Math.max(maxWidth, titleWidth);
         }

         String speedText = String.format("%." + this.decimalPlaces.get() + "f KM/H", this.currentSpeed);
         double speedWidth = renderer.textWidth(speedText, (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         renderer.text(speedText, curX, curY, (Color)this.speedColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
         height += textHeight;
         maxWidth = Math.max(maxWidth, speedWidth);
         this.setSize(maxWidth, height);
      } else {
         if (this.isInEditor()) {
            String demoText = this.showTitle.get() ? "Speed\n25.3 KM/H" : "25.3 KM/H";
            renderer.text(demoText, this.x, this.y, (Color)this.speedColor.get(), (Boolean)this.textShadow.get(), (Double)this.textScale.get());
            this.setSize(
               renderer.textWidth(demoText, (Boolean)this.textShadow.get(), (Double)this.textScale.get()),
               renderer.textHeight((Boolean)this.textShadow.get(), (Double)this.textScale.get())
            );
         }
      }
   }

   private void updateSpeed() {
      PlayerEntity player = MeteorClient.mc.player;
      if (player != null) {
         double velX = player.getVelocity().x;
         double velZ = player.getVelocity().z;
         double velY = player.getVelocity().y;
         double speed;
         if ((Boolean)this.showHorizontalOnly.get()) {
            speed = Math.sqrt(velX * velX + velZ * velZ);
         } else {
            speed = Math.sqrt(velX * velX + velZ * velZ + velY * velY);
         }

         this.currentSpeed = speed * 20.0 * 3.6;
      }
   }
}
