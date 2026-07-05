package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.ElytraTakeoff;
import com.hunterbuddy.modules.regear.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class ElytraRecast extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgNether = this.settings.createGroup("Nether");
   private final SettingGroup sgOverworld = this.settings.createGroup("Overworld/End");
   private final Setting<Boolean> disableIfNoRockets = this.sgGeneral
      .add(
         new Builder().name("disable-if-no-rockets")
                  .description("Automatically disable ElytraRecast when no firework rockets are available.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> debugMessages = this.sgGeneral
      .add(
         new Builder().name("debug-messages").description("Show debug messages in chat for state changes and recovery events.")
               .defaultValue(false)
            .build()
      );
   private final Setting<Integer> activationDelay = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("activation-delay")
                  .description("Ticks to wait between jump and elytra activation attempts.")
               .defaultValue(3)
            .range(1, 20)
            .sliderRange(1, 20)
            .build()
      );
   private final Setting<Integer> rocketDelay = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("rocket-delay")
                  .description("Ticks between rocket usages during ascent.")
               .defaultValue(15)
            .range(5, 60)
            .sliderRange(5, 60)
            .build()
      );
   private final Setting<Double> rotationSpeed = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("rotation-speed")
               .description("Degrees per tick to adjust pitch when ascending. Lower = smoother & more realistic.")
            .defaultValue(3.0)
            .range(0.5, 15.0)
            .sliderRange(0.5, 15.0)
            .build()
      );
   private final Setting<Double> targetPitch = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("target-pitch")
               .description("Target pitch angle when ascending. Negative = looking up.")
            .defaultValue(-70.0)
            .range(-89.0, 0.0)
            .sliderRange(-89.0, 0.0)
            .build()
      );
   private final Setting<Integer> netherFallDelay = this.sgNether
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("fall-delay")
                  .description("Ticks to wait after stopping flight before triggering recovery in Nether.")
               .defaultValue(5)
            .range(1, 40)
            .sliderRange(1, 40)
            .build()
      );
   private final Setting<Boolean> usePitch40Bounds = this.sgOverworld
      .add(
         new Builder().name("use-pitch40-bounds")
                  .description("Use Pitch40's lower bounds as the minimum altitude instead of the setting below.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Integer> overworldTargetAltitude = this.sgOverworld
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("target-altitude")
                  .description("Target Y level to ascend to in Overworld/End.")
               .defaultValue(360)
            .sliderRange(50, 400)
            .build()
      );
   private final Setting<Integer> overworldMinAltitude = this.sgOverworld
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("min-altitude")
                     .description("Trigger recovery if Y drops below this in Overworld/End. Ignored if use-pitch40-bounds is enabled.")
                  .defaultValue(310)
               .sliderRange(10, 400)
               .visible(() -> !(Boolean)this.usePitch40Bounds.get())
               .build()
      );
   private ElytraRecast.State state;
   private int tickCounter;
   private int rocketTickCounter;
   private boolean wasGliding;
   private boolean usedActivationRocket;
   private int notGlidingTicks;
   private boolean netherRocketUsed;
   private Pitch40 pitch40Util;
   private final ElytraTakeoff takeoff = new ElytraTakeoff();
   private int monitoringRecoveryCooldown = 0;
   private static final int MONITORING_RECOVERY_PAUSE = 80;

   public ElytraRecast() {
      super(HunterBuddyAddon.HUNTER_BUDDY_CATEGORY, "ElytraRecast", "Flight recovery fallback. Monitors and recovers when you stop flying or drop too low.");
   }

   public boolean isAscending() {
      return this.state == ElytraRecast.State.ASCENDING;
   }

   public boolean isRecovering() {
      return this.takeoff.isActive() || this.state == ElytraRecast.State.ASCENDING;
   }

   public void onActivate() {
      if (this.mc.player != null) {
         if (!this.hasElytraEquipped()) {
            this.error("No elytra equipped!", new Object[0]);
            this.toggle();
         } else {
            this.tickCounter = 0;
            this.rocketTickCounter = 0;
            this.wasGliding = this.mc.player.isGliding();
            this.usedActivationRocket = false;
            this.notGlidingTicks = 0;
            this.netherRocketUsed = false;
            this.takeoff.cancel();
            this.monitoringRecoveryCooldown = 0;
            this.pitch40Util = (Pitch40)Modules.get().get(Pitch40.class);
            this.state = ElytraRecast.State.MONITORING;
            if ((Boolean)this.debugMessages.get()) {
               this.info("Monitoring flight...", new Object[0]);
            }
         }
      }
   }

   public void onDeactivate() {
      this.takeoff.cancel();
      Utils.setPressed(this.mc.options.jumpKey, false);
      this.state = null;
   }

   public void requestTakeoff() {
      if (this.isActive() && this.mc.player != null) {
         this.monitoringRecoveryCooldown = 0;
         this.beginRecovery("manual takeoff requested");
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.world != null && this.state != null && this.hasElytraEquipped()) {
         if (this.takeoff.isActive()) {
            this.takeoff.tickPre();
            return;
         }

         this.tickCounter++;
         this.rocketTickCounter++;
         if (this.state == ElytraRecast.State.MONITORING) {
            this.handleMonitoring();
         } else if (this.state == ElytraRecast.State.ASCENDING) {
            this.handleAscending();
         }
      }
   }

   @EventHandler
   private void onTickPost(Post event) {
      if (this.mc.player != null && this.mc.world != null && this.takeoff.isActive()) {
         this.takeoff.tickPost();
      }
   }

   private void handleMonitoring() {
      if (this.takeoff.isActive()) {
         return;
      }

      if (this.monitoringRecoveryCooldown > 0) {
         this.monitoringRecoveryCooldown--;
         this.wasGliding = this.mc.player.isGliding();
         return;
      }

      boolean currentlyGliding = this.mc.player.isGliding();
      double currentY = this.mc.player.getY();
      if (this.isInNether()) {
         if (!currentlyGliding) {
            this.notGlidingTicks++;
            if (this.notGlidingTicks >= (Integer)this.netherFallDelay.get()) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Nether: Flight stopped for " + this.notGlidingTicks + " ticks! Recovering...", new Object[0]);
               }

               this.triggerRecovery();
               this.notGlidingTicks = 0;
               return;
            }
         } else {
            this.notGlidingTicks = 0;
         }
      } else {
         double minAlt = this.getEffectiveMinAltitude();
         if (currentY < minAlt) {
            if (!currentlyGliding) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Below min altitude (Y=" + (int)currentY + " < " + (int)minAlt + ") and not flying! Recovering...", new Object[0]);
               }

               this.triggerRecovery();
               this.wasGliding = false;
               return;
            }

            if (this.isPitch40ManagingFlight()) {
               this.wasGliding = currentlyGliding;
               return;
            }

            if ((Boolean)this.debugMessages.get()) {
               this.info("Altitude too low (Y=" + (int)currentY + " < " + (int)minAlt + ")! Ascending...", new Object[0]);
            }

            this.triggerRecovery();
            return;
         }
      }

      this.wasGliding = currentlyGliding;
   }

   private double getEffectiveMinAltitude() {
      return this.usePitch40Bounds.get() && this.pitch40Util != null && this.pitch40Util.isActive()
         ? (Double)this.pitch40Util.pitch40LowerBounds.get() - 10.0
         : ((Integer)this.overworldMinAltitude.get()).intValue();
   }

   private boolean isPitch40ManagingFlight() {
      return this.pitch40Util != null && this.pitch40Util.isActive();
   }

   public void resetToMonitoring() {
      if (this.isActive()) {
         this.state = ElytraRecast.State.MONITORING;
         this.tickCounter = 0;
         this.rocketTickCounter = 0;
         this.usedActivationRocket = false;
         this.notGlidingTicks = 0;
         this.netherRocketUsed = false;
         this.takeoff.cancel();
         if (this.mc.player != null) {
            this.wasGliding = this.mc.player.isGliding();
         }
      }
   }

   private void triggerRecovery() {
      this.beginRecovery("recovery triggered");
   }

   private void beginRecovery(String reason) {
      if (!this.hasRockets()) {
         if ((Boolean)this.disableIfNoRockets.get()) {
            this.error("No firework rockets available! Disabling ElytraRecast.", new Object[0]);
            this.toggle();
            return;
         }

         this.warning("No firework rockets available! Recovery may fail.", new Object[0]);
      }

      if ((Boolean)this.debugMessages.get()) {
         this.info(reason, new Object[0]);
      }

      this.tickCounter = 0;
      this.rocketTickCounter = (Integer)this.rocketDelay.get();
      this.usedActivationRocket = false;
      if (this.mc.player.isGliding()) {
         this.enterAscending();
      } else {
         this.startTakeoff();
      }
   }

   private void startTakeoff() {
      this.takeoff.start(-1, new ElytraTakeoff.Listener() {
         @Override
         public void onTakeoffSuccess(double speedBps) {
            ElytraRecast.this.enterAscending();
            if ((Boolean)ElytraRecast.this.debugMessages.get()) {
               ElytraRecast.this.info("Recovery takeoff complete at " + String.format("%.1f", speedBps) + " b/s", new Object[0]);
            }
         }

         @Override
         public void onTakeoffFailed(String reason) {
            ElytraRecast.this.state = ElytraRecast.State.MONITORING;
            ElytraRecast.this.monitoringRecoveryCooldown = MONITORING_RECOVERY_PAUSE;
            ElytraRecast.this.tickCounter = 0;
            if ((Boolean)ElytraRecast.this.debugMessages.get()) {
               ElytraRecast.this.warning("Recovery takeoff failed: " + reason, new Object[0]);
            }
         }

         @Override
         public void onTakeoffDebug(String message) {
            if ((Boolean)ElytraRecast.this.debugMessages.get()) {
               ElytraRecast.this.info(message, new Object[0]);
            }
         }
      });
   }

   private void enterAscending() {
      this.state = ElytraRecast.State.ASCENDING;
      this.rocketTickCounter = (Integer)this.rocketDelay.get();
      this.tickCounter = 0;
      if (!this.useRocketWhileGliding() && (Boolean)this.disableIfNoRockets.get()) {
         this.error("No rockets for ascent! Disabling.", new Object[0]);
         this.toggle();
      }
   }

   private void handleAscending() {
      if (!this.mc.player.isGliding()) {
         if (!this.hasRockets() && (Boolean)this.disableIfNoRockets.get()) {
            this.error("Lost flight and no rockets! Disabling.", new Object[0]);
            this.toggle();
         } else {
            this.startTakeoff();
         }
      } else if (this.isPitch40ManagingFlight()) {
         this.state = ElytraRecast.State.MONITORING;
         this.wasGliding = true;
         this.tickCounter = 0;
      } else {
         if (this.isInNether()) {
            this.adjustPitchUp();
            if (!this.netherRocketUsed) {
               if (this.useRocket()) {
                  this.netherRocketUsed = true;
               } else if ((Boolean)this.disableIfNoRockets.get()) {
                  this.error("No rockets for Nether recovery! Disabling.", new Object[0]);
                  this.toggle();
                  return;
               }
            }

            if (this.tickCounter > 10) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Nether recovery complete - baritone takes over.", new Object[0]);
               }

               this.state = ElytraRecast.State.MONITORING;
               this.wasGliding = true;
               this.tickCounter = 0;
               this.netherRocketUsed = false;
               return;
            }
         } else {
            double currentY = this.mc.player.getY();
            int target = (Integer)this.overworldTargetAltitude.get();
            if (currentY >= target) {
               if ((Boolean)this.debugMessages.get()) {
                  this.info("Target altitude Y=" + target + " reached! Resuming monitoring.", new Object[0]);
               }

               this.state = ElytraRecast.State.MONITORING;
               this.wasGliding = true;
               this.tickCounter = 0;
               return;
            }

            this.adjustPitchUp();
            if (this.rocketTickCounter >= (Integer)this.rocketDelay.get()) {
               if (this.useRocket()) {
                  this.rocketTickCounter = 0;
               } else if ((Boolean)this.disableIfNoRockets.get()) {
                  this.error("Out of rockets during ascent! Disabling.", new Object[0]);
                  this.toggle();
                  return;
               }
            }
         }
      }
   }

   private void adjustPitchUp() {
      float currentPitch = this.mc.player.getPitch();
      float target = ((Double)this.targetPitch.get()).floatValue();
      float speed = ((Double)this.rotationSpeed.get()).floatValue();
      float diff = target - currentPitch;
      if (!(Math.abs(diff) < 0.5F)) {
         float adjustment;
         if (Math.abs(diff) <= speed) {
            adjustment = diff;
         } else {
            adjustment = diff > 0.0F ? speed : -speed;
         }

         float newPitch = currentPitch + adjustment;
         newPitch = Math.max(-89.0F, Math.min(89.0F, newPitch));
         this.mc.player.setPitch(newPitch);
      }
   }

   private boolean hasRockets() {
      FindItemResult hotbar = InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET});
      if (hotbar.found()) {
         return true;
      }

      FindItemResult inv = InvUtils.find(new Item[]{Items.FIREWORK_ROCKET});
      return inv.found();
   }

   private boolean useRocket() {
      return this.useRocketWhileGliding();
   }

   private boolean useRocketWhileGliding() {
      if (this.mc.player == null || this.mc.interactionManager == null) {
         return false;
      }

      if (!this.mc.player.isGliding()) {
         return false;
      }

      FindItemResult hotbar = InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET});
      if (!hotbar.found()) {
         FindItemResult inv = InvUtils.find(new Item[]{Items.FIREWORK_ROCKET});
         if (!inv.found()) {
            return false;
         }

         int hotbarSlot = this.findEmptyHotbarSlot();
         if (hotbarSlot != -1) {
            InvUtils.move().from(inv.slot()).to(hotbarSlot);
            hotbar = InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET});
         }
      }

      if (!hotbar.found()) {
         return false;
      }

      if (hotbar.isOffhand()) {
         this.mc.interactionManager.interactItem(this.mc.player, Hand.OFF_HAND);
         this.mc.player.swingHand(Hand.OFF_HAND);
         return true;
      }

      ItemStack selected = this.mc.player.getMainHandStack();
      if (selected.getItem() == Items.FIREWORK_ROCKET) {
         this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
         this.mc.player.swingHand(Hand.MAIN_HAND);
         return true;
      }

      return Utils.firework(this.mc, false) != -1;
   }

   private int findEmptyHotbarSlot() {
      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getStack(i).isEmpty()) {
            return i;
         }
      }

      return -1;
   }

   private boolean hasElytraEquipped() {
      return this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
   }

   private boolean isInNether() {
      return this.mc.world == null ? false : this.mc.world.getRegistryKey() == World.NETHER;
   }

   public String getInfoString() {
      if (this.state == null) {
         return null;
      }

      if (this.mc.player == null) {
         return this.state.name();
      }

      double minAlt = this.isInNether() ? 0.0 : this.getEffectiveMinAltitude();

      if (this.takeoff.isActive()) {
         return "TAKEOFF " + this.takeoff.getPhase();
      }

      return switch (this.state) {
         case MONITORING -> this.isInNether()
            ? String.format("Y=%.0f", this.mc.player.getY())
            : String.format("Y=%.0f (min:%.0f)", this.mc.player.getY(), minAlt);
         case JUMPING, ACTIVATING -> "TAKEOFF";
         case ASCENDING -> this.isInNether()
            ? String.format("\u2191%.0f", this.mc.player.getY())
            : String.format("\u2191%.0f/%d", this.mc.player.getY(), this.overworldTargetAltitude.get());
      };
   }

   private enum State {
      MONITORING,
      JUMPING,
      ACTIVATING,
      ASCENDING;
   }
}
