package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class RocketFly extends Module {
   private long lastRocketUse = 0L;
   private boolean launched = false;
   private double yTarget = -1.0;
   private float targetPitch = 0.0F;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> fireworkDelay = this.sgGeneral
      .add(
         new Builder().name("timed-delay").description("The delay between firework usages in milliseconds.").defaultValue(4000)
            .sliderRange(0, 10000)
            .build()
      );
   private final Setting<Boolean> useManualY = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("use-manual-y-level")
                  .description("Use a manually set Y level instead of the Y level when activated.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Integer> manualYLevel = this.sgGeneral
      .add(
         new Builder().name("manual-y-level")
                        .description("The Y level to maintain when using manual Y level.")
                     .defaultValue(256)
                  .sliderRange(-64, 320)
                  .visible(this.useManualY::get)
               .onChanged(val -> this.yTarget = val.intValue()).build()
      );

   public RocketFly() {
      super(HunterBuddyAddon.HUNT_CATEGORY, "RocketFly", "Maintains a level Y-flight with fireworks and smooth pitch control.");
   }

   public void onActivate() {
      this.launched = false;
      this.yTarget = -1.0;
      if (this.mc.player == null || !this.mc.player.isGliding()) {
         this.info("You must be flying before enabling RocketFly.", new Object[0]);
      }
   }

   public void tickFlyLogic() {
      if (this.mc.player != null) {
         double currentY = this.mc.player.getY();
         if (this.mc.player.isGliding()) {
            if (this.yTarget == -1.0 || !this.launched) {
               if ((Boolean)this.useManualY.get()) {
                  this.yTarget = ((Integer)this.manualYLevel.get()).intValue();
               } else {
                  this.yTarget = currentY;
               }

               this.launched = true;
            }

            if (!(Boolean)this.useManualY.get()) {
               double yDiffFromLock = currentY - this.yTarget;
               if (Math.abs(yDiffFromLock) > 10.0) {
                  this.yTarget = currentY;
                  this.info("Y-lock reset due to altitude deviation.", new Object[0]);
               }
            }

            double yDiff = currentY - this.yTarget;
            if (Math.abs(yDiff) > 10.0) {
               this.targetPitch = (float)(Math.atan2(yDiff, 100.0) * (180.0 / Math.PI));
            } else if (yDiff > 2.0) {
               this.targetPitch = 10.0F;
            } else if (yDiff < -2.0) {
               this.targetPitch = -10.0F;
            } else {
               this.targetPitch = 0.0F;
            }

            float currentPitch = this.mc.player.getPitch();
            float pitchDiff = this.targetPitch - currentPitch;
            this.mc.player.setPitch(currentPitch + pitchDiff * 0.1F);
            if (System.currentTimeMillis() - this.lastRocketUse > ((Integer)this.fireworkDelay.get()).intValue()) {
               this.tryUseFirework();
            }
         } else {
            if (!this.launched) {
               this.mc.player.jump();
               this.launched = true;
            } else if (System.currentTimeMillis() - this.lastRocketUse > 1000L) {
               this.tryUseFirework();
            }

            this.yTarget = -1.0;
         }
      }
   }

   public void resetYLock() {
      this.yTarget = -1.0;
      this.launched = false;
   }

   @EventHandler
   private void onTick(Pre event) {
      this.tickFlyLogic();
   }

   private void tryUseFirework() {
      FindItemResult hotbar = InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET});
      if (!hotbar.found()) {
         FindItemResult inv = InvUtils.find(new Item[]{Items.FIREWORK_ROCKET});
         if (!inv.found()) {
            this.info("No fireworks found in hotbar or inventory.", new Object[0]);
            return;
         }

         int hotbarSlot = this.findEmptyHotbarSlot();
         if (hotbarSlot == -1) {
            this.info("No empty hotbar slot available to move fireworks.", new Object[0]);
            return;
         }

         InvUtils.move().from(inv.slot()).to(hotbarSlot);
      }

      Utils.firework(this.mc, false);
      this.lastRocketUse = System.currentTimeMillis();
   }

   private int findEmptyHotbarSlot() {
      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            return i;
         }
      }

      return -1;
   }
}
