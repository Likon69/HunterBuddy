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

   private final Setting<Boolean> showFlight = sgDisplay.add(new Builder()
      .name("show-flight")
      .description("Gliding time left on the worn elytra. Durability is seconds: one point is spent per second in the air, whether or not you boost.")
      .defaultValue(true).build());

   private final Setting<Boolean> showElytraCount = sgDisplay.add(new Builder()
      .name("show-elytra-count").description("How many usable elytras you carry, the worn one included.")
      .defaultValue(true).build());

   private final Setting<Boolean> showTotalFlight = sgDisplay.add(new Builder()
      .name("show-total-flight").description("Gliding time of every usable elytra added together.")
      .defaultValue(true).build());

   private final Setting<Boolean> showRange = sgDisplay.add(new Builder()
      .name("show-range")
      .description("How far that total carries you at your current speed. Reads as -- while you are not moving.")
      .defaultValue(false).build());

   private final Setting<Double> unusablePct = sgDisplay.add(
      new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
         .name("unusable-pct")
         .description("Durability kept in reserve on every elytra. Below it an elytra is not counted and its time is not added, so a planned leg never ends on a broken chestplate.")
         .defaultValue(2.0).min(0.0).sliderRange(0.0, 20.0).build());

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
      // Padding on each side of the separator, scaled with the text so the line
      // does not fall apart when the element is enlarged.
      double pad = (this.compactSpacing.get() ? 2.0 : 5.0) * scale;
      double curX = this.x;
      double totalWidth = 0.0;

      // Flipped by the first segment that actually draws, so the separator is
      // never emitted before it. Which field comes first depends on the toggles,
      // so it cannot be decided ahead of time.
      boolean first = true;

      if (this.showSpeed.get()) {
         // Whole numbers: the decimal is unreadable at flight speed and costs
         // two glyphs on a line whose whole point is to be short.
         curX = this.drawSegment(renderer, curX, this.y, null, String.format("%.0f", bps), " bps",
            this.valueColor.get(), shadow, scale, first, pad);
         totalWidth = curX - this.x;
         first = false;
      }

      if (this.showPitch.get()) {
         SettingColor pitchColor = pitch <= this.pitchDangerBelow.get() ? this.dangerColor.get()
            : (pitch >= this.pitchWarnAbove.get() ? this.warnColor.get() : this.valueColor.get());
         // The degree sign rides with the value rather than greying out: split off
         // it reads as a separate token instead of part of the angle.
         curX = this.drawSegment(renderer, curX, this.y, null, String.format("%.0f°", pitch), null,
            pitchColor, shadow, scale, first, pad);
         totalWidth = curX - this.x;
         first = false;
      }

      if (this.showRockets.get() && (!this.hideRocketsIfZero.get() || rockets > 0)) {
         SettingColor rocketColor = rockets < 0 ? new SettingColor(128, 128, 128, 255)
            : (rockets == 0 ? this.dangerColor.get()
            : (rockets < this.lowRocketThreshold.get() ? this.warnColor.get() : this.valueColor.get()));
         String rocketsStr = rockets < 0 ? "--" : String.valueOf(rockets);
         curX = this.drawSegment(renderer, curX, this.y, null, rocketsStr, rockets < 0 ? null : "r",
            rocketColor, shadow, scale, first, pad);
         totalWidth = curX - this.x;
         first = false;
      }

      if (this.showDurability.get()) {
         int pct = (durability > 0 && maxDurability > 0) ? (int) ((durability * 100.0) / maxDurability) : -1;
         SettingColor durColor;
         String durText;
         String durUnit;
         if (!hasElytra) {
            durColor = new SettingColor(128, 128, 128, 255);
            durText = "None";
            durUnit = null;
         } else if (pct <= 0) {
            durColor = this.dangerColor.get();
            durText = "BROKEN";
            durUnit = null;
         } else {
            durColor = pct < this.lowDurabilityPct.get() ? this.warnColor.get() : this.valueColor.get();
            // A percentage instead of 342/432: same information, half the width,
            // and it is the form the warning threshold is expressed in anyway.
            durText = String.valueOf(pct);
            durUnit = "%";
         }
         curX = this.drawSegment(renderer, curX, this.y, null, durText, durUnit,
            durColor, shadow, scale, first, pad);
         totalWidth = curX - this.x;
         first = false;
      }

      if (this.showFlight.get()) {
         int seconds = this.flightSecondsOf(chest);
         SettingColor flightColor;

         if (!hasElytra) {
            flightColor = new SettingColor(128, 128, 128, 255);
         } else if (seconds <= 0) {
            flightColor = this.dangerColor.get();
         } else {
            // Reuses the durability warning threshold so both fields turn at once.
            flightColor = (seconds * 100.0) / maxDurability < this.lowDurabilityPct.get()
               ? this.warnColor.get() : this.valueColor.get();
         }

         // No unit: m:ss says what it is on its own.
         curX = this.drawSegment(renderer, curX, this.y, null,
            hasElytra ? formatTime(seconds) : "--", null, flightColor, shadow, scale, first, pad);
         totalWidth = curX - this.x;
         first = false;
      }

      int spares = (this.showElytraCount.get() || this.showRange.get()) ? this.countUsableElytras() : 0;
      int totalSeconds = (this.showTotalFlight.get() || this.showRange.get()) ? this.totalFlightSeconds() : 0;

      if (this.showElytraCount.get()) {
         SettingColor countColor = spares == 0 ? this.dangerColor.get()
            : (spares == 1 ? this.warnColor.get() : this.valueColor.get());
         curX = this.drawSegment(renderer, curX, this.y, "x", String.valueOf(spares), null,
            countColor, shadow, scale, first, pad);
         totalWidth = curX - this.x;
         first = false;
      }

      if (this.showTotalFlight.get()) {
         // The one field that keeps a written prefix: two m:ss values on the same
         // line are indistinguishable otherwise.
         curX = this.drawSegment(renderer, curX, this.y, "tot ", formatTime(totalSeconds), null,
            totalSeconds > 0 ? this.valueColor.get() : this.dangerColor.get(), shadow, scale, first, pad);
         totalWidth = curX - this.x;
         first = false;
      }

      if (this.showRange.get()) {
         // Seconds of glide times current ground speed. Only honest while moving,
         // which is also the only moment the number is worth reading.
         double blocks = totalSeconds * bps;
         String rangeText;
         String rangeUnit;
         if (bps < 1.0) {
            rangeText = "--";
            rangeUnit = null;
         } else if (blocks >= 1000.0) {
            rangeText = String.format("%.1f", blocks / 1000.0);
            rangeUnit = "km";
         } else {
            rangeText = String.format("%.0f", blocks);
            rangeUnit = "m";
         }
         curX = this.drawSegment(renderer, curX, this.y, null, rangeText, rangeUnit,
            this.valueColor.get(), shadow, scale, first, pad);
         totalWidth = curX - this.x;
      }

      this.setSize(Math.max(totalWidth, 80.0), textHeight);
   }

   /**
    * Draws one field as grey prefix, coloured value, grey unit.
    *
    * <p>The separator is written by the segment that <em>follows</em> it, never
    * appended by the one before. Trailing it would leave a dot hanging off the
    * end of the line whenever the last enabled field changed — and which field is
    * last is a runtime question, since every one of them has its own toggle.
    */
   private double drawSegment(HudRenderer renderer, double curX, double curY,
                              String prefix, String value, String unit, SettingColor valueColor,
                              boolean shadow, double scale, boolean first, double pad) {
      double x = curX;

      if (!first) {
         x += pad;
         renderer.text("·", x, curY, this.labelColor.get(), shadow, scale);
         x += renderer.textWidth("·", shadow, scale) + pad;
      }

      if (prefix != null) {
         renderer.text(prefix, x, curY, this.labelColor.get(), shadow, scale);
         x += renderer.textWidth(prefix, shadow, scale);
      }

      renderer.text(value, x, curY, valueColor, shadow, scale);
      x += renderer.textWidth(value, shadow, scale);

      if (unit != null) {
         renderer.text(unit, x, curY, this.labelColor.get(), shadow, scale);
         x += renderer.textWidth(unit, shadow, scale);
      }

      return x;
   }

   /**
    * Seconds of gliding left in a stack, or 0 when it is at or under the reserve.
    *
    * <p>A gliding item takes one point of damage every twenty ticks — verified in
    * {@code LivingEntity}: the wear fires when {@code fallFlyTicks % 10 == 0} and
    * the resulting count is even. Remaining durability is therefore remaining
    * seconds of flight, one for one. Nothing here depends on fireworks; boosting
    * covers more ground in the same seconds, it does not spend the elytra faster.
    */
   private int flightSecondsOf(ItemStack stack) {
      if (!stack.isOf(Items.ELYTRA)) return 0;

      int max = stack.getMaxDamage();
      if (max <= 0) return 0;

      // The reserve is what stops you landing on a broken elytra at 20k blocks out.
      int reserve = Math.max(1, (int) Math.ceil(max * this.unusablePct.get() / 100.0));
      return Math.max(0, max - stack.getDamage() - reserve);
   }

   /** Wearable elytras in the inventory, the equipped one included. */
   private int countUsableElytras() {
      int count = 0;
      // Bounded to the backpack, not to size(): PlayerInventory.size() is
      // main.size() + EQUIPMENT_SLOTS.size(), so the loop would walk over the
      // chest slot and the worn elytra would then be counted a second time by the
      // explicit add below. That inflated the spare count by one — the "only one
      // left" warning could never fire — and the total by a full elytra, several
      // kilometres of range that do not exist.
      var inventory = MeteorClient.mc.player.getInventory().getMainStacks();

      for (int i = 0; i < inventory.size(); i++) {
         if (this.flightSecondsOf(inventory.get(i)) > 0) count++;
      }

      if (this.flightSecondsOf(MeteorClient.mc.player.getEquippedStack(EquipmentSlot.CHEST)) > 0) count++;

      return count;
   }

   /** Total gliding seconds across every usable elytra, equipped one included. */
   private int totalFlightSeconds() {
      int total = 0;
      // Bounded to the backpack, not to size(): PlayerInventory.size() is
      // main.size() + EQUIPMENT_SLOTS.size(), so the loop would walk over the
      // chest slot and the worn elytra would then be counted a second time by the
      // explicit add below. That inflated the spare count by one — the "only one
      // left" warning could never fire — and the total by a full elytra, several
      // kilometres of range that do not exist.
      var inventory = MeteorClient.mc.player.getInventory().getMainStacks();

      for (int i = 0; i < inventory.size(); i++) {
         total += this.flightSecondsOf(inventory.get(i));
      }

      return total + this.flightSecondsOf(MeteorClient.mc.player.getEquippedStack(EquipmentSlot.CHEST));
   }

   private static String formatTime(int seconds) {
      return String.format("%d:%02d", seconds / 60, seconds % 60);
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