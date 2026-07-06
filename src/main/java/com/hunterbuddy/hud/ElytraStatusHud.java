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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

public class ElytraStatusHud extends HudElement {
   public static final HudElementInfo<ElytraStatusHud> INFO = new HudElementInfo(
      HunterBuddyAddon.HUD_GROUP, "ElytraStatus",
      "Compact one-line elytra status: speed, pitch, rockets, durability.",
      ElytraStatusHud::new
   );

   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgDisplay = settings.createGroup("Display");
   private final SettingGroup sgColors = settings.createGroup("Colors");

   private final Setting<Boolean> showSpeed = sgDisplay.add(new Builder()
      .name("show-speed").description("Show horizontal speed (BPS).").defaultValue(true).build());

   private final Setting<Boolean> showPitch = sgDisplay.add(new Builder()
      .name("show-pitch").description("Show pitch angle.").defaultValue(true).build());

   private final Setting<Boolean> showRockets = sgDisplay.add(new Builder()
      .name("show-rockets").description("Show firework rocket count in inventory.")
      .defaultValue(true).build());

   private final Setting<Boolean> showDurability = sgDisplay.add(new Builder()
      .name("show-durability").description("Show equipped elytra durability.")
      .defaultValue(true).build());

   private final Setting<Integer> lowRocketThreshold = sgDisplay.add(
      new meteordevelopment.meteorclient.settings.IntSetting.Builder()
         .name("low-rockets-threshold")
         .description("Rockets count below this triggers warning color.")
         .defaultValue(10).min(1).max(100).sliderRange(1, 50)
         .build()
   );

   private final Setting<Integer> lowDurabilityPct = sgDisplay.add(
      new meteordevelopment.meteorclient.settings.IntSetting.Builder()
         .name("low-durability-pct")
         .description("Durability % below this triggers warning color.")
         .defaultValue(30).min(5).max(80).sliderRange(5, 50)
         .build()
   );

   private final Setting<Integer> pitchWarnAbove = sgDisplay.add(
      new meteordevelopment.meteorclient.settings.IntSetting.Builder()
         .name("pitch-warn-above")
         .description("Pitch above this (looking down) triggers warning color.")
         .defaultValue(45).min(20).max(80).sliderRange(20, 70)
         .build()
   );

   private final Setting<Integer> pitchDangerBelow = sgDisplay.add(
      new meteordevelopment.meteorclient.settings.IntSetting.Builder()
         .name("pitch-danger-below")
         .description("Pitch below this (looking up) triggers danger color.")
         .defaultValue(-45).min(-80).max(-20).sliderRange(-70, -20)
         .build()
   );

   private final Setting<Boolean> hideRocketsIfZero = sgDisplay.add(new Builder()
      .name("hide-rockets-if-zero")
      .description("Hide the Rockets field entirely when count is 0 (cleaner display).")
      .defaultValue(false).build()
   );

   private final Setting<Double> textScale = sgGeneral.add(
      new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
         .name("text-scale").description("Scale of the text.")
         .defaultValue(1.0).min(0.5).max(2.0).sliderRange(0.5, 2.0).build()
   );

   private final Setting<Boolean> textShadow = sgGeneral.add(new Builder()
      .name("text-shadow").description("Render shadow behind text.").defaultValue(true).build());

   private final Setting<Boolean> compactSpacing = sgDisplay.add(new Builder()
      .name("compact-spacing").description("Use minimal spacing between fields.")
      .defaultValue(true).build());

   private final Setting<SettingColor> labelColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("label-color").description("Color of the labels (BPS:, Pitch:, etc).")
         .defaultValue(new SettingColor(150, 150, 150, 255)).build()
   );

   private final Setting<SettingColor> valueColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("value-color").description("Color of values when in normal range.")
         .defaultValue(new SettingColor(255, 255, 255, 255)).build()
   );

   private final Setting<SettingColor> warnColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("warn-color").description("Color when value is low (warning).")
         .defaultValue(new SettingColor(255, 200, 80, 255)).build()
   );

   private final Setting<SettingColor> dangerColor = sgColors.add(
      new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
         .name("danger-color").description("Color when value is critical.")
         .defaultValue(new SettingColor(255, 80, 80, 255)).build()
   );

   public ElytraStatusHud() {
      super(INFO);
   }

   @Override
   public void render(HudRenderer renderer) {
      if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) {
         this.setSize(80.0, renderer.textHeight(this.textShadow.get(), this.textScale.get()));
         return;
      }

      Vec3d vel = MeteorClient.mc.player.getVelocity();
      double bps = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0;
      float pitch = MeteorClient.mc.player.getPitch();

      // Cache elytra ItemStack once (avoid double getEquippedStack call)
      ItemStack chest = MeteorClient.mc.player.getEquippedStack(EquipmentSlot.CHEST);
      boolean hasElytra = chest.getItem() == Items.ELYTRA;
      int durability = hasElytra ? chest.getMaxDamage() - chest.getDamage() : -1;
      int maxDurability = hasElytra ? chest.getMaxDamage() : -1;
      int rockets = hasElytra ? this.countRockets() : -1;

      boolean shadow = this.textShadow.get();
      double scale = this.textScale.get();
      double textHeight = renderer.textHeight(shadow, scale);
      double spacing = this.compactSpacing.get() ? 4.0 : 8.0;
      double curX = this.x;
      double totalWidth = 0.0;

      if (this.showSpeed.get()) {
         curX = this.drawField(renderer, curX, this.y, "BPS: ", String.format("%.1f", bps), this.valueColor.get(),
            shadow, scale, spacing);
         totalWidth = curX - this.x;
      }

      if (this.showPitch.get()) {
         SettingColor pitchColor = pitch <= this.pitchDangerBelow.get() ? this.dangerColor.get()
            : (pitch >= this.pitchWarnAbove.get() ? this.warnColor.get() : this.valueColor.get());
         curX = this.drawField(renderer, curX, this.y, "Pitch: ", String.format("%.1f°", pitch), pitchColor,
            shadow, scale, spacing);
         totalWidth = curX - this.x;
      }

      if (this.showRockets.get() && (!this.hideRocketsIfZero.get() || rockets > 0)) {
         SettingColor rocketColor = rockets < 0 ? new SettingColor(128, 128, 128, 255)
            : (rockets == 0 ? this.dangerColor.get()
            : (rockets < this.lowRocketThreshold.get() ? this.warnColor.get() : this.valueColor.get()));
         String rocketsStr = rockets < 0 ? "--" : String.valueOf(rockets);
         curX = this.drawField(renderer, curX, this.y, "Rockets: ", rocketsStr, rocketColor,
            shadow, scale, spacing);
         totalWidth = curX - this.x;
      }

      if (this.showDurability.get()) {
         int pct = (durability > 0 && maxDurability > 0) ? (int) ((durability * 100.0) / maxDurability) : -1;
         SettingColor durColor;
         String durText;
         if (!hasElytra) {
            durColor = new SettingColor(128, 128, 128, 255);
            durText = "None";
         } else if (pct <= 0) {
            durColor = this.dangerColor.get();
            durText = "BROKEN";
         } else {
            durColor = pct < this.lowDurabilityPct.get() ? this.warnColor.get() : this.valueColor.get();
            durText = durability + "/" + maxDurability;
         }
         curX = this.drawField(renderer, curX, this.y, "Dur: ", durText, durColor,
            shadow, scale, spacing);
         totalWidth = curX - this.x;
      }

      this.setSize(Math.max(totalWidth, 80.0), textHeight);
   }

   private double drawField(HudRenderer renderer, double curX, double curY,
                             String label, String value, SettingColor valueColor,
                             boolean shadow, double scale, double spacing) {
      double lw = renderer.textWidth(label, shadow, scale);
      renderer.text(label, curX, curY, this.labelColor.get(), shadow, scale);
      double vw = renderer.textWidth(value, shadow, scale);
      renderer.text(value, curX + lw, curY, valueColor, shadow, scale);
      return curX + lw + vw + spacing;
   }

   private int countRockets() {
      int count = 0;
      for (int i = 0; i < MeteorClient.mc.player.getInventory().size(); i++) {
         ItemStack stack = MeteorClient.mc.player.getInventory().getStack(i);
         if (stack.isOf(Items.FIREWORK_ROCKET)) {
            count += stack.getCount();
         }
      }
      return count;
   }
}