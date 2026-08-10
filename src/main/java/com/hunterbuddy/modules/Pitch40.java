package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.modules.regear.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFly;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class Pitch40 extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgFirework = this.settings.createGroup("Auto Firework");
   private final SettingGroup sgRecast = this.settings.createGroup("Auto Recast");
   public final Setting<Double> pitch40LowerBounds = this.sgGeneral
      .add(
         new Builder().name("pitch40-lower-bounds")
                  .description(
                     "The bottom height boundary for pitch40. After descending below this boundary you will start pitching upwards. Synced with ElytraFly."
                  ).defaultValue(360.0)
               .min(-128.0)
               .sliderRange(-64.0, 500.0)
               .onChanged(this::syncLowerBoundsToElytraFly).build()
      );
   public final Setting<Double> pitch40UpperBounds = this.sgGeneral
      .add(
         new Builder().name("pitch40-upper-bounds")
                  .description(
                     "The upper height boundary for pitch40. When ascending above this boundary you will start pitching downwards. Synced with ElytraFly."
                  ).defaultValue(420.0)
               .min(-128.0)
               .sliderRange(-64.0, 500.0)
               .onChanged(this::syncUpperBoundsToElytraFly).build()
      );
   public final Setting<Boolean> autoFirework = this.sgFirework
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("auto-firework")
                  .description("Uses a firework automatically when speed is too low or height drops below bounds.")
               .defaultValue(true)
            .build()
      );
   public final Setting<Double> minSpeed = this.sgFirework
      .add(
         new Builder().name("min-speed")
                  .description("Fire rocket when speed drops below this (blocks/sec). Normal Pitch40 maintains ~30-40 b/s.")
               .defaultValue(5.0)
               .sliderRange(10.0, 50.0)
               .visible(this.autoFirework::get)
            .build()
      );
   public final Setting<Integer> fireworkCooldownTicks = this.sgFirework
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("cooldown-ticks")
                     .description("Minimum ticks between firework uses.")
                  .defaultValue(100)
               .sliderRange(10, 100)
               .visible(this.autoFirework::get)
            .build()
      );
   public final Setting<Boolean> autoRecast = this.sgRecast
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("auto-recast")
                  .description("Automatically enable ElytraRecast when Pitch40 activates. ElytraRecast will monitor your flight and recover if you fall.")
               .defaultValue(true)
            .build()
      );
   public final Setting<Double> rotationSpeedUp = this.sgRecast
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                        .name("rotation-speed-up")
                     .description(
                        "How fast the pitch rotates upward (deg/tick) when ascending. Synced to ElytraFly.pitch40rotationSpeedUp. "
                     + "Lower this if the server rolls you back when pitching up."
                     )
                  .defaultValue(5.45)
               .min(0.1)
            .sliderRange(0.1, 20.0)
            .onChanged(this::syncRotationSpeedUp).build()
      );
   public final Setting<Double> rotationSpeedDown = this.sgRecast
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                        .name("rotation-speed-down")
                     .description(
                        "How fast the pitch rotates downward (deg/tick) when descending. Synced to ElytraFly.pitch40rotationSpeedDown."
                     )
                  .defaultValue(0.90)
               .min(0.1)
            .sliderRange(0.1, 20.0)
            .onChanged(this::syncRotationSpeedDown).build()
      );
   private Module elytraFly;
   private ElytraFlightModes oldValue;
   private Setting<ElytraFlightModes> elytraFlyMode;
   private int fireworkCooldown = 0;
   private boolean goingUp = false;
   private int elytraSwapSlot = -1;
   private double peakY = 0.0;
   private double troughY = 0.0;
   private ElytraRecast elytraRecast = null;
   private boolean oldElytraAutoFirework = false;
   private RegistryKey<World> lastDimension = null;
   private boolean userDisabledElytraFly = false;
   private boolean wasElytraFlyActive = false;
   private int elytraFlyRetryDelay = 0;
   private static final int ELYTRA_FLY_RETRY_DELAY_TICKS = 20;

   public Pitch40() {
      super(HunterBuddyAddon.HUNT_CATEGORY, "Pitch40", "Utility for Pitch40 elytra flying. Syncs bounds with ElytraFly and auto-enables on reconnect.");
   }

   /** Which half of the cycle the flight is in right now. Read by the Pitch40Cycle HUD. */
   public boolean isClimbing() {
      return this.goingUp;
   }

   private Module getElytraFly() {
      if (this.elytraFly == null) {
         this.elytraFly = Modules.get().get(ElytraFly.class);
      }

      return this.elytraFly;
   }

   private Setting<ElytraFlightModes> getElytraFlyMode() {
      if (this.elytraFlyMode == null) {
         this.elytraFlyMode = (Setting<ElytraFlightModes>) this.getElytraFly().settings.get("mode");
      }

      return this.elytraFlyMode;
   }

   private void syncLowerBoundsToElytraFly(Double value) {
      if (value != null && this.getElytraFly() != null) {
         Setting<Double> elytraLower = (Setting<Double>) this.getElytraFly().settings.get("pitch40-lower-bounds");
         if (elytraLower != null && !((Double)elytraLower.get()).equals(value)) {
            elytraLower.set(value);
         }
      }
   }

   private void syncUpperBoundsToElytraFly(Double value) {
      if (value != null && this.getElytraFly() != null) {
         Setting<Double> elytraUpper = (Setting<Double>) this.getElytraFly().settings.get("pitch40-upper-bounds");
         if (elytraUpper != null && !((Double)elytraUpper.get()).equals(value)) {
            elytraUpper.set(value);
         }
      }
   }

   private void syncRotationSpeedUp(Double value) {
      if (value != null && this.getElytraFly() != null) {
         Setting<Double> elytraRotationUp = (Setting<Double>) this.getElytraFly().settings.get("pitch40-rotation-speed-up");
         if (elytraRotationUp != null && !((Double)elytraRotationUp.get()).equals(value)) {
            elytraRotationUp.set(value);
         }
      }
   }

   private void syncRotationSpeedDown(Double value) {
      if (value != null && this.getElytraFly() != null) {
         Setting<Double> elytraRotationDown = (Setting<Double>) this.getElytraFly().settings.get("pitch40-rotation-speed-down");
         if (elytraRotationDown != null && !((Double)elytraRotationDown.get()).equals(value)) {
            elytraRotationDown.set(value);
         }
      }
   }

   public void onActivate() {
      this.oldValue = (ElytraFlightModes)this.getElytraFlyMode().get();
      this.getElytraFlyMode().set(ElytraFlightModes.Pitch40);
      this.fireworkCooldown = 0;
      this.goingUp = false;
      this.elytraFlyRetryDelay = 0;
      this.peakY = this.mc.player != null ? this.mc.player.getY() : 0.0;
      this.troughY = this.peakY;
      this.elytraRecast = (ElytraRecast)Modules.get().get(ElytraRecast.class);
      this.lastDimension = this.mc.world != null ? this.mc.world.getRegistryKey() : null;
      this.userDisabledElytraFly = false;
      this.wasElytraFlyActive = this.getElytraFly().isActive();
      this.syncLowerBoundsToElytraFly((Double)this.pitch40LowerBounds.get());
      this.syncUpperBoundsToElytraFly((Double)this.pitch40UpperBounds.get());
      this.syncRotationSpeedUp((Double)this.rotationSpeedUp.get());
      this.syncRotationSpeedDown((Double)this.rotationSpeedDown.get());
      if ((Boolean)this.autoRecast.get() && this.elytraRecast != null && !this.elytraRecast.isActive()) {
         this.elytraRecast.toggle();
      }

      if ((Boolean)this.autoFirework.get() && this.getElytraFly().settings.get("auto-firework") instanceof BoolSetting boolSetting) {
         this.oldElytraAutoFirework = (Boolean)boolSetting.get();
         boolSetting.set(false);
      }
   }

   public void onDeactivate() {
      if (this.getElytraFly().isActive()) {
         this.getElytraFly().toggle();
      }

      this.getElytraFlyMode().set(this.oldValue);
      if ((Boolean)this.autoFirework.get() && this.getElytraFly().settings.get("auto-firework") instanceof BoolSetting boolSetting) {
         boolSetting.set(this.oldElytraAutoFirework);
      }

      if ((Boolean)this.autoRecast.get() && this.elytraRecast != null && this.elytraRecast.isActive()) {
         this.elytraRecast.toggle();
      }

      RotationUtils.getInstance().clearRotations();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         RegistryKey<World> currentDimension = this.mc.world.getRegistryKey();
         if (this.lastDimension != null && !this.lastDimension.equals(currentDimension)) {
            this.info("Dimension changed! Syncing bounds to ElytraFly.", new Object[0]);
            this.lastDimension = currentDimension;
            this.userDisabledElytraFly = false;
            this.elytraFlyRetryDelay = 0;
            this.syncLowerBoundsToElytraFly((Double)this.pitch40LowerBounds.get());
            this.syncUpperBoundsToElytraFly((Double)this.pitch40UpperBounds.get());
            this.peakY = this.mc.player.getY();
            this.troughY = this.peakY;
            this.goingUp = false;
         } else {
            this.lastDimension = currentDimension;
            boolean elytraFlyActive = this.getElytraFly().isActive();
            boolean elytraRecastRecovering = this.elytraRecast != null && this.elytraRecast.isActive() && this.elytraRecast.isRecovering();
            if (this.wasElytraFlyActive && !elytraFlyActive && !elytraRecastRecovering) {
               this.userDisabledElytraFly = true;
            }

            this.wasElytraFlyActive = elytraFlyActive;
            if (!elytraFlyActive) {
               boolean isValidDimension = World.OVERWORLD.equals(currentDimension) || World.END.equals(currentDimension);
               if (this.elytraFlyRetryDelay > 0) {
                  this.elytraFlyRetryDelay--;
                  this.goingUp = false;
               } else {
                  if (!this.userDisabledElytraFly && isValidDimension) {
                     this.syncLowerBoundsToElytraFly((Double)this.pitch40LowerBounds.get());
                     this.syncUpperBoundsToElytraFly((Double)this.pitch40UpperBounds.get());
                     this.getElytraFly().toggle();
                     if (this.getElytraFly().isActive()) {
                        this.wasElytraFlyActive = true;
                        this.userDisabledElytraFly = false;
                        this.elytraFlyRetryDelay = 0;
                     } else {
                        this.elytraFlyRetryDelay = 20;
                     }
                  }

                  this.goingUp = false;
               }
            } else {
               if (this.fireworkCooldown > 0) {
                  this.fireworkCooldown--;
               }

               if (this.elytraSwapSlot != -1) {
                  InvUtils.swap(this.elytraSwapSlot, true);
                  this.mc.interactionManager.interactItem(this.mc.player, Hand.MAIN_HAND);
                  InvUtils.swapBack();
                  this.elytraSwapSlot = -1;
               }

               double playerY = this.mc.player.getY();
               double velocityY = this.mc.player.getVelocity().y;
               boolean wasGoingUp = this.goingUp;
               this.goingUp = velocityY > 0.0;
               if (wasGoingUp && !this.goingUp) {
                  this.peakY = playerY;
               } else if (!wasGoingUp && this.goingUp) {
                  this.troughY = playerY;
               }

               if (this.goingUp && playerY > this.peakY) {
                  this.peakY = playerY;
               } else if (!this.goingUp && playerY < this.troughY) {
                  this.troughY = playerY;
               }

               this.checkAndUseFirework();
            }
         }
      }
   }

   private double getSpeedBPS() {
      Vec3d velocity = this.mc.player.getVelocity();
      double speedPerTick = Math.sqrt(
         velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z
      );
      return speedPerTick * 20.0;
   }

   private void checkAndUseFirework() {
      if ((Boolean)this.autoFirework.get() && this.fireworkCooldown <= 0) {
         if (this.mc.player != null && this.mc.player.isGliding()) {
            if (this.elytraRecast == null || !this.elytraRecast.isActive() || !this.elytraRecast.isRecovering()) {
               double currentSpeed = this.getSpeedBPS();
               double playerY = this.mc.player.getY();
               double lowerBounds = (Double)this.pitch40LowerBounds.get();
               boolean needsBoost = currentSpeed < (Double)this.minSpeed.get();
               boolean emergency = playerY < lowerBounds - 10.0;
               if (needsBoost || emergency) {
                  int launchStatus = Utils.firework(this.mc, false);
                  if (launchStatus >= 0) {
                     this.fireworkCooldown = (Integer)this.fireworkCooldownTicks.get();
                     if (launchStatus != 200) {
                        this.elytraSwapSlot = launchStatus;
                     }

                     this.peakY = playerY;
                     this.troughY = playerY;
                  }
               }
            }
         }
      }
   }
}
