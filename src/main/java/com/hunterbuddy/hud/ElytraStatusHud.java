package com.hunterbuddy.hud;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.util.ElytraFlightMath;
import com.hunterbuddy.util.HudGlowPanel;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.enchantment.Enchantments;
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

   private final SettingGroup sgPanel = settings.createGroup("Panel");
   private final HudGlowPanel panel = new HudGlowPanel(this.sgPanel);

   /**
    * Segments queued for this frame, so the panel can be sized and drawn under them.
    *
    * <p>The line used to be drawn field by field as it was computed, which meant its width was
    * only known once everything was already on screen — too late to put anything behind it.
    */
   private final List<Segment> segments = new ArrayList<>();

   /**
    * State of the smooth countdown on the worn elytra.
    *
    * <p>Durability only drops once every few seconds — four, at Unbreaking III — and the time is
    * that durability multiplied back up. So the reading sat still and then fell by four at once,
    * which looks like a fault even though the arithmetic is right. These carry the last real
    * measurement and when it was taken, so the seconds in between can be counted off one by one.
    */
   private int flightAnchorSeconds = -1;
   private int flightAnchorDurability = -1;
   private long flightAnchorAt;
   private HudGlowPanel.Severity severity = HudGlowPanel.Severity.OK;

   private record Segment(String prefix, String value, String unit, SettingColor color, boolean first) {
   }

   public ElytraStatusHud() {
      super(INFO);
   }

   @Override
   public void render(HudRenderer renderer) {
      if (MeteorClient.mc.player == null || MeteorClient.mc.world == null) {
         this.setSize(80.0, renderer.textHeight(this.textShadow.get(), this.textScale.get()));
         return;
      }

      this.segments.clear();
      this.severity = HudGlowPanel.Severity.OK;

      Vec3d vel = MeteorClient.mc.player.getVelocity();
      double bps = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0;
      float pitch = MeteorClient.mc.player.getPitch();

      // Cache elytra ItemStack once (avoid double getEquippedStack call)
      ItemStack chest = MeteorClient.mc.player.getEquippedStack(EquipmentSlot.CHEST);
      boolean hasElytra = chest.getItem() == Items.ELYTRA;
      int durability = hasElytra ? chest.getMaxDamage() - chest.getDamage() : -1;
      int maxDurability = hasElytra ? chest.getMaxDamage() : -1;
      int rockets = hasElytra ? this.countRockets() : -1;

      // Flying without one is the worst state the line can report, and no field says so on its
      // own: the durability slot just reads "None" in grey.
      if (!hasElytra) this.severity = HudGlowPanel.Severity.CRITICAL;

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
         int seconds = this.smoothFlightSeconds(chest, durability);
         SettingColor flightColor;

         if (!hasElytra) {
            flightColor = new SettingColor(128, 128, 128, 255);
         } else if (seconds <= 0) {
            flightColor = this.dangerColor.get();
         } else {
            // Measured against durability, not against seconds. Once Unbreaking multiplies
            // the time, seconds can run to several times the maximum durability, and a
            // percentage built from them would sit above a hundred for most of the flight —
            // the warning would simply never arrive. Durability is the quantity the threshold
            // is written in, so both fields still turn at the same moment.
            int pctLeft = (durability * 100) / maxDurability;
            flightColor = pctLeft < this.lowDurabilityPct.get()
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

      // The content sits one padding in, so the panel it stands on lands exactly on the element
      // box. Drawn from the raw origin instead, the panel would spill past the top and left of
      // what the editor lets you grab.
      double width = Math.max(totalWidth, 80.0);
      double inset = this.panel.padding();
      this.panel.draw(renderer, this.x + inset, this.y + inset, width, textHeight, this.severity);
      this.flushSegments(renderer, shadow, scale, pad, inset);

      this.setSize(width + inset * 2.0, textHeight + inset * 2.0);
   }

   /**
    * Draws one field as grey prefix, coloured value, grey unit.
    *
    * <p>The separator is written by the segment that <em>follows</em> it, never
    * appended by the one before. Trailing it would leave a dot hanging off the
    * end of the line whenever the last enabled field changed — and which field is
    * last is a runtime question, since every one of them has its own toggle.
    */
   /** Queues one field and returns where the next would start. */
   private double drawSegment(HudRenderer renderer, double curX, double curY,
                              String prefix, String value, String unit, SettingColor valueColor,
                              boolean shadow, double scale, boolean first, double pad) {
      this.segments.add(new Segment(prefix, value, unit, valueColor, first));
      this.severity = HudGlowPanel.worst(this.severity, severityOf(valueColor));

      return curX + this.segmentWidth(renderer, prefix, value, unit, first, shadow, scale, pad);
   }

   /**
    * Severity read back from the colour the field chose.
    *
    * <p>Taken from the colour rather than recomputed: every threshold already decides one, and
    * a second copy of that logic would be a second thing to keep in agreement. The comparison
    * is by reference, which holds because a setting hands back the same instance every call.
    */
   private HudGlowPanel.Severity severityOf(SettingColor color) {
      if (color == this.dangerColor.get()) return HudGlowPanel.Severity.CRITICAL;
      if (color == this.warnColor.get()) return HudGlowPanel.Severity.WARN;
      return HudGlowPanel.Severity.OK;
   }

   private double segmentWidth(HudRenderer renderer, String prefix, String value, String unit,
                               boolean first, boolean shadow, double scale, double pad) {
      double w = 0.0;
      if (!first) w += pad * 2 + renderer.textWidth("·", shadow, scale);
      if (prefix != null) w += renderer.textWidth(prefix, shadow, scale);
      w += renderer.textWidth(value, shadow, scale);
      if (unit != null) w += renderer.textWidth(unit, shadow, scale);
      return w;
   }

   /** Draws the queued fields left to right, once the panel is underneath them. */
   private void flushSegments(HudRenderer renderer, boolean shadow, double scale, double pad, double inset) {
      double x = this.x + inset;
      double y = this.y + inset;

      for (Segment segment : this.segments) {
         if (!segment.first) {
            x += pad;
            renderer.text("·", x, y, this.labelColor.get(), shadow, scale);
            x += renderer.textWidth("·", shadow, scale) + pad;
         }

         if (segment.prefix != null) {
            renderer.text(segment.prefix, x, y, this.labelColor.get(), shadow, scale);
            x += renderer.textWidth(segment.prefix, shadow, scale);
         }

         renderer.text(segment.value, x, y, segment.color, shadow, scale);
         x += renderer.textWidth(segment.value, shadow, scale);

         if (segment.unit != null) {
            renderer.text(segment.unit, x, y, this.labelColor.get(), shadow, scale);
            x += renderer.textWidth(segment.unit, shadow, scale);
         }
      }
   }

   /**
    * The worn elytra's remaining seconds, counted down continuously.
    *
    * <p>Re-anchored on every genuine durability change, so it can never drift away from the
    * truth: between two drops it merely spends the seconds the last drop bought, and the moment
    * a real one lands it snaps back to whatever the item says.
    *
    * <p>Only ticks down while you are gliding. On the ground the elytra is not being spent, and a
    * number falling while you stand still would be a lie.
    */
   private int smoothFlightSeconds(ItemStack chest, int durability) {
      int actual = this.flightSecondsOf(chest);

      if (durability != this.flightAnchorDurability || actual > this.flightAnchorSeconds) {
         this.flightAnchorDurability = durability;
         this.flightAnchorSeconds = actual;
         this.flightAnchorAt = System.currentTimeMillis();
         return actual;
      }

      if (MeteorClient.mc.player == null || !MeteorClient.mc.player.isGliding()) {
         this.flightAnchorAt = System.currentTimeMillis();
         return actual;
      }

      int elapsed = (int) ((System.currentTimeMillis() - this.flightAnchorAt) / 1000L);
      return Math.max(0, actual - elapsed);
   }

   private int flightSecondsOf(ItemStack stack) {
      return ElytraFlightMath.flightSecondsOf(stack, this.unusablePct.get());
   }

   private int countUsableElytras() {
      return ElytraFlightMath.countUsableElytras(MeteorClient.mc.player, this.unusablePct.get());
   }

   private int totalFlightSeconds() {
      return ElytraFlightMath.totalUsableSeconds(MeteorClient.mc.player, this.unusablePct.get());
   }

   /**
    * Minutes and seconds, or hours and minutes once there is more than an hour of it.
    *
    * <p>Past sixty minutes the m:ss form stops being read as a duration at all: twenty elytras
    * came out as {@code 549:00}, which looks like a count of something rather than nine hours
    * of flight. The worn elytra keeps the familiar form, since it never reaches an hour.
    */
   private static String formatTime(int seconds) {
      if (seconds >= 3600) return String.format("%dh %02dm", seconds / 3600, (seconds % 3600) / 60);
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