package com.hunterbuddy.modules;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.modules.regear.util.Utils;
import com.hunterbuddy.util.ChestSwapBurst;
import com.hunterbuddy.util.GlideClearHolder;
import java.util.Locale;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
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
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public class Pitch40 extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgFirework = this.settings.createGroup("Auto Firework");
   private final SettingGroup sgRecast = this.settings.createGroup("Auto Recast");
   private final SettingGroup sgChestSwap = this.settings.createGroup("Chest Swap");
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
   public final Setting<RocketMode> rockets = this.sgFirework
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<RocketMode>()
                     .name("rockets")
                  .description(
                     "What the rockets are for. Off flies the free glide alone. Keep spends one only when the corridor is losing: on the next climb whenever a cycle topped out short of the upper bounds by more than height-tolerance, and below the lower bounds when the glide has stalled. Boost is Keep plus one on every climb, or every Nth with climbs-per-rocket."
                  ).defaultValue(RocketMode.Keep)
            .build()
      );
   public final Setting<Double> heightTolerance = this.sgFirework
      .add(
         new Builder().name("height-tolerance")
                  .description("How far short of the upper bounds a cycle may top out before Keep calls it a losing corridor and spends a rocket on the next climb.")
               .defaultValue(10.0)
               .min(0.0)
               .sliderRange(0.0, 60.0)
               .visible(() -> this.rockets.get() != RocketMode.Off)
               .build()
      );
   public final Setting<Integer> climbsPerRocket = this.sgFirework
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("climbs-per-rocket")
                     .description("In Boost, one rocket every this many climbs. One means every climb.")
                  .defaultValue(1)
               .min(1)
               .sliderRange(1, 10)
               .visible(() -> this.rockets.get() == RocketMode.Boost)
               .build()
      );
   public final Setting<Boolean> rocketBoost = this.sgFirework
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("rocket-boost")
                  .description(
                     "Ride the boost window on each of these rockets. While a rocket is attached the server accepts any velocity inside a box around its own prediction, and the ride flies the fastest point of that box for the rocket's whole life. Turns the Boost mode's forty blocks a second into about fifty-six on the same rockets."
                  ).defaultValue(false)
               .visible(() -> this.rockets.get() != RocketMode.Off)
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
   public final Setting<Boolean> chestSwap = this.sgChestSwap
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chest-swap")
                  .description(
                     "Glides with a chest piece worn and the elytra in a hand: every swap-interval ticks one burst swaps the elytra in, restarts the glide and swaps the chest piece straight back inside one tick, so the elytra never takes durability. Needs the pair, a glider and a chest piece, one worn and the other in either hand."
                  ).defaultValue(false)
            .build()
      );
   public final Setting<Integer> swapInterval = this.sgChestSwap
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("swap-interval")
                     .description(
                        "Fewest ticks between two bursts. It is a floor, not an appointment: a burst only departs once the server's glide clear has actually arrived, otherwise the start would be refused. Durability never moves whatever the value, since the worn chest piece makes the server drop the glide counter back to zero on the burst tick itself."
                     ).defaultValue(8)
               .min(2)
               .sliderRange(2, 40)
               .visible(this.chestSwap::get)
               .build()
      );
   public final Setting<Integer> minAirBelow = this.sgChestSwap
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("min-air-below")
                     .description(
                        "Fewest blocks of clear air under all four corners of the feet before a burst may run. Between bursts the server falls a body of its own at terminal speed, about four blocks a tick, and the moment that fall touches ground it refuses the next start, so this is the margin that keeps it in the air. Two blocks of headroom are required as well, since the server carries a standing hitbox while the client glides."
                     )
                  .defaultValue(6)
               .min(3)
               .sliderRange(3, 32)
               .visible(this.chestSwap::get)
               .build()
      );
   public final Setting<Boolean> logSwaps = this.sgChestSwap
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("log-swaps")
                  .description("Writes one line to the game log for every burst, hand-back and loss of clearance, with the vertical speed and the depth that was required. Off means no trace to measure a flight with.")
               .defaultValue(true)
               .visible(this.chestSwap::get)
            .build()
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
   private volatile Hand chestSwapBurstHand = null;
   private volatile long chestSwapNextBurst = 0L;
   private String chestSwapBlockedReason = null;
   private int chestSwapMisses = 0;
   private boolean chestSwapJumpHeld = false;
   private boolean rocketArmed = false;
   private boolean rocketPending = false;
   private long rocketHoldUntil = 0L;
   private int climbsSinceRocket = 0;
   private float lastPitch = 0.0F;
   private com.hunterbuddy.bephax.BepBoost bepBoost = null;
   private boolean boostToggledByUs = false;
   private int lastTickAge = -1;
   private int lastClearanceDepth = 0;
   private boolean clearanceWasOk = true;

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
      this.chestSwapBurstHand = null;
      this.chestSwapNextBurst = 0L;
      this.chestSwapBlockedReason = null;
      this.rocketArmed = false;
      this.rocketPending = false;
      this.climbsSinceRocket = 0;
      this.userDisabledElytraFly = false;
      this.wasElytraFlyActive = this.getElytraFly().isActive();
      this.syncLowerBoundsToElytraFly((Double)this.pitch40LowerBounds.get());
      this.syncUpperBoundsToElytraFly((Double)this.pitch40UpperBounds.get());
      this.syncRotationSpeedUp((Double)this.rotationSpeedUp.get());
      this.syncRotationSpeedDown((Double)this.rotationSpeedDown.get());
      if ((Boolean)this.autoRecast.get() && this.elytraRecast != null && !this.elytraRecast.isActive()) {
         this.elytraRecast.toggle();
      }

      this.bepBoost = Modules.get().get(com.hunterbuddy.bephax.BepBoost.class);
      this.boostToggledByUs = false;
      if ((Boolean)this.rocketBoost.get() && this.bepBoost != null && !this.bepBoost.isActive()) {
         this.bepBoost.toggle();
         this.boostToggledByUs = true;
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

      if (this.boostToggledByUs && this.bepBoost != null && this.bepBoost.isActive()) {
         this.bepBoost.toggle();
      }

      this.boostToggledByUs = false;

      this.handBackGlide();
      GlideClearHolder.release(this);
      this.releaseChestSwapJump();
      this.chestSwapBurstHand = null;
      RotationUtils.getInstance().clearRotations();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.world != null) {
         // A module another module toggled on can end up subscribed twice after
         // a reconnect and then ticks twice per game tick, which halves the
         // swap interval and sends a glide start with no jump behind it. One
         // pass per player tick, whatever the bus thinks.
         if (this.lastTickAge == this.mc.player.age) {
            return;
         }

         this.lastTickAge = this.mc.player.age;
         if ((Boolean)this.rocketBoost.get() && this.bepBoost != null && this.bepBoost.isActive() && this.mc.player.isGliding()) {
            this.bepBoost.declareTravelling();
         }

         this.serviceChestSwap();
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
                  if (this.rockets.get() != RocketMode.Off
                     && this.peakY < (Double)this.pitch40UpperBounds.get() - (Double)this.heightTolerance.get()) {
                     this.rocketArmed = true;
                  }
               } else if (!wasGoingUp && this.goingUp) {
                  this.troughY = playerY;
                  if (this.rockets.get() == RocketMode.Boost && ++this.climbsSinceRocket >= (Integer)this.climbsPerRocket.get()) {
                     this.climbsSinceRocket = 0;
                     this.rocketArmed = true;
                  }
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

   @Override
   public String getInfoString() {
      if (!(Boolean)this.chestSwap.get()) {
         return null;
      }

      if (this.chestSwapBlockedReason != null) {
         return "paired wrong";
      }

      return this.clearanceWasOk ? "ground clear" : "within reach, needs " + this.lastClearanceDepth;
   }

   private void serviceChestSwap() {
      this.releaseChestSwapJump();
      if (!(Boolean)this.chestSwap.get()) {
         GlideClearHolder.release(this);
         this.chestSwapBurstHand = null;
         this.chestSwapBlockedReason = null;
         this.chestSwapMisses = 0;
         return;
      }

      if (this.elytraRecast != null && this.elytraRecast.isActive() && this.elytraRecast.isRecovering()) {
         if (this.handBackGlide()) {
            GlideClearHolder.release(this);
         }

         return;
      }

      boolean gliderWorn = ChestSwapBurst.isGlider(this.mc.player.getEquippedStack(EquipmentSlot.CHEST));
      if (!this.mc.player.isGliding()) {
         this.handBackGlide();
         GlideClearHolder.release(this);
         return;
      }

      String problem = ChestSwapBurst.pairProblem(this.mc.player);
      if (problem != null && !gliderWorn) {
         this.reportChestSwapBlocked(problem);
         if (this.handBackGlide()) {
            GlideClearHolder.release(this);
         }

         return;
      }

      boolean clearance = this.clearanceOk();
      if (clearance != this.clearanceWasOk) {
         this.clearanceWasOk = clearance;
         this.logSwap(clearance ? "clearance-back" : "no-clearance", "held=" + GlideClearHolder.holding());
      }

      if (!clearance) {
         if (this.handBackGlide()) {
            GlideClearHolder.release(this);
         }

         return;
      }

      this.chestSwapBlockedReason = null;
      if (!GlideClearHolder.claim(this)) {
         return;
      }

      long age = this.mc.player.age;
      int interval = (Integer)this.swapInterval.get();
      if (gliderWorn) {
         if (age < this.rocketHoldUntil || ChestSwapBurst.rocketAttached(this.mc.player)) {
            return;
         }

         Hand chestHand = ChestSwapBurst.chestPieceHand(this.mc.player);
         if (chestHand != null && age >= this.chestSwapNextBurst) {
            ChestSwapBurst.swapSilently(chestHand);
            this.chestSwapNextBurst = age + 2L;
            if (++this.chestSwapMisses >= 6) {
               this.chestSwapMisses = 0;
               this.chestSwapNextBurst = age + 200L;
               this.reportChestSwapBlocked("the server keeps refusing the chest swap, gliding on the worn elytra for ten seconds");
            }
         }

         return;
      }

      if (!GlideClearHolder.holding()) {
         GlideClearHolder.answerPings();
         return;
      }

      if (age < this.chestSwapNextBurst && age - GlideClearHolder.heldSince() <= 2L * interval) {
         return;
      }

      Hand gliderHand = ChestSwapBurst.gliderHand(this.mc.player);
      if (gliderHand == null) {
         return;
      }

      this.burst(gliderHand, true);
      this.chestSwapMisses = 0;
   }

   private void burst(Hand gliderHand, boolean swapBack) {
      long age = this.mc.player.age;
      long waited = age - GlideClearHolder.heldSince();
      GlideClearHolder.flush();
      if (gliderHand != null) {
         ChestSwapBurst.swapSilently(gliderHand);
      } else {
         ChestSwapBurst.wearGlider(this.mc.player);
      }

      this.mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(this.mc.player, Mode.START_FALL_FLYING));
      this.mc.player.startGliding();
      this.mc.options.jumpKey.setPressed(true);
      this.chestSwapJumpHeld = true;
      this.chestSwapBurstHand = swapBack ? gliderHand : null;
      this.chestSwapNextBurst = age + (Integer)this.swapInterval.get();
      if (this.rocketPending) {
         this.fireRocket();
         if (!this.rocketPending) {
            this.chestSwapBurstHand = null;
            this.rocketHoldUntil = age + 10L;
         }
      }

      this.logSwap(swapBack ? "burst" : "hand-back", "waited=" + waited);
   }

   private void logSwap(String what, String fields) {
      if ((Boolean)this.logSwaps.get()) {
         HunterBuddyAddon.LOG
            .info(
               String.format(
                  Locale.ROOT,
                  "[Pitch40 chest-swap] %s vy=%.3f depth=%d y=%.1f %s",
                  what,
                  this.mc.player.getVelocity().y,
                  this.lastClearanceDepth,
                  this.mc.player.getY(),
                  fields
               )
            );
      }
   }

   private void releaseChestSwapJump() {
      if (this.chestSwapJumpHeld) {
         this.chestSwapJumpHeld = false;
         this.mc.options.jumpKey.setPressed(false);
      }
   }

   private boolean handBackGlide() {
      if (this.mc.player == null) {
         return true;
      }

      this.chestSwapBurstHand = null;
      if (ChestSwapBurst.isGlider(this.mc.player.getEquippedStack(EquipmentSlot.CHEST))) {
         return true;
      }

      Hand gliderHand = ChestSwapBurst.gliderHand(this.mc.player);
      if (!this.mc.player.isGliding()) {
         ChestSwapBurst.wearGlider(this.mc.player);
         return true;
      }

      if (gliderHand == null && ChestSwapBurst.gliderSlot(this.mc.player) == -1) {
         return true;
      }

      if (!GlideClearHolder.owns(this) || !GlideClearHolder.holding()) {
         return false;
      }

      this.burst(gliderHand, false);
      return true;
   }

   private void reportChestSwapBlocked(String reason) {
      if (reason.equals(this.chestSwapBlockedReason)) {
         return;
      }

      this.chestSwapBlockedReason = reason;
      this.info(reason, new Object[0]);
   }

   private boolean clearanceOk() {
      double x = this.mc.player.getX();
      double y = this.mc.player.getY();
      double z = this.mc.player.getZ();
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
         return false;
      }

      int feet = MathHelper.floor(y);
      BlockPos.Mutable pos = new BlockPos.Mutable();

      for (int above = 1; above <= 2; above++) {
         pos.set(MathHelper.floor(x), feet + above, MathHelper.floor(z));
         if (!this.mc.world.getBlockState(pos).getCollisionShape(this.mc.world, pos).isEmpty()) {
            return false;
         }
      }

      double descent = Math.max(0.0, -this.mc.player.getVelocity().y);
      int depth = Math.max((Integer)this.minAirBelow.get(), MathHelper.ceil(descent * 2.0 * (Integer)this.swapInterval.get()));
      this.lastClearanceDepth = depth;

      for (int cornerX = -1; cornerX <= 1; cornerX += 2) {
         for (int cornerZ = -1; cornerZ <= 1; cornerZ += 2) {
            if (!this.columnClear(x + cornerX * 0.3, z + cornerZ * 0.3, feet, depth)) {
               return false;
            }
         }
      }

      return true;
   }

   private boolean columnClear(double x, double z, int feet, int depth) {
      BlockPos.Mutable pos = new BlockPos.Mutable();
      int blockX = MathHelper.floor(x);
      int blockZ = MathHelper.floor(z);

      for (int dy = 1; dy <= depth; dy++) {
         pos.set(blockX, feet - dy, blockZ);
         if (!this.mc.world.getBlockState(pos).getCollisionShape(this.mc.world, pos).isEmpty()) {
            return false;
         }
      }

      return true;
   }

   @EventHandler
   private void onPlaySound(meteordevelopment.meteorclient.events.world.PlaySoundEvent event) {
      if ((Boolean)this.chestSwap.get()) {
         for (net.minecraft.util.Identifier identifier : java.util.List.of(
            net.minecraft.util.Identifier.of("minecraft:item.armor.equip_generic"),
            net.minecraft.util.Identifier.of("minecraft:item.armor.equip_netherite"),
            net.minecraft.util.Identifier.of("minecraft:item.armor.equip_elytra"),
            net.minecraft.util.Identifier.of("minecraft:item.armor.equip_diamond"),
            net.minecraft.util.Identifier.of("minecraft:item.armor.equip_gold"),
            net.minecraft.util.Identifier.of("minecraft:item.armor.equip_iron"),
            net.minecraft.util.Identifier.of("minecraft:item.armor.equip_chain"),
            net.minecraft.util.Identifier.of("minecraft:item.armor.equip_leather"),
            net.minecraft.util.Identifier.of("minecraft:item.elytra.flying")
         )) {
            if (identifier.equals(event.sound.getId())) {
               event.cancel();
               break;
            }
         }
      }
   }

   @EventHandler
   private void onSendMovementPackets(SendMovementPacketsEvent.Pre event) {
      if (this.chestSwapBurstHand != null && this.mc.player != null) {
         Hand hand = this.chestSwapBurstHand;
         this.chestSwapBurstHand = null;
         ChestSwapBurst.swapSilently(hand);
      }
   }

   @EventHandler
   private void onReceivePacket(Receive event) {
      net.minecraft.client.network.ClientPlayerEntity player = this.mc.player;
      if (!(Boolean)this.chestSwap.get() || player == null) {
         return;
      }

      if (event.packet instanceof PlayerPositionLookS2CPacket
         || event.packet instanceof net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket
         || event.packet instanceof net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
         || event.packet instanceof net.minecraft.network.packet.s2c.play.ExplosionS2CPacket explosion && explosion.playerKnockback().isPresent()
         || event.packet instanceof net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket velocity
            && velocity.getEntityId() == player.getId()) {
         GlideClearHolder.flushAheadOfTeleport();
         this.chestSwapBurstHand = null;
         this.chestSwapNextBurst = player.age + (Integer)this.swapInterval.get();
         return;
      }

      GlideClearHolder.intake(this, event);
   }

   private double getSpeedBPS() {
      Vec3d velocity = this.mc.player.getVelocity();
      double speedPerTick = Math.sqrt(
         velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z
      );
      return speedPerTick * 20.0;
   }

   private void checkAndUseFirework() {
      float pitch = this.mc.player == null ? 0.0F : this.mc.player.getPitch();
      boolean levellingOut = pitch > this.lastPitch;
      this.lastPitch = pitch;
      if (this.rockets.get() == RocketMode.Off || !(Boolean)this.autoFirework.get() || this.fireworkCooldown > 0) {
         return;
      }

      if (this.mc.player == null || !this.mc.player.isGliding()) {
         return;
      }

      if (this.elytraRecast != null && this.elytraRecast.isActive() && this.elytraRecast.isRecovering()) {
         return;
      }

      double playerY = this.mc.player.getY();
      if (playerY < (Double)this.pitch40LowerBounds.get() && this.getSpeedBPS() < (Double)this.minSpeed.get()) {
         this.rocketArmed = true;
      }

      // The one point of the cycle a rocket pays at. Fired at the foot of the
      // climb it brakes: the thrust is 0.85 x look - 0.5 x velocity, and with
      // the speed already past 1.7 blocks a tick along the look that term is
      // negative. On the slow way back down through level, near the top, the
      // speed is at its lowest and the push is almost all gain, along a look
      // flat enough to hold the height it just bought.
      if (!this.rocketArmed || !levellingOut || pitch < 0.0F || pitch > 10.0F) {
         return;
      }

      if ((Boolean)this.chestSwap.get()) {
         this.rocketPending = true;
         return;
      }

      this.fireRocket();
   }

   /**
    * The server only spawns a rocket for a player it believes is gliding, so
    * with the chest swap on this is only ever called from inside a burst,
    * after the glide start and before the chest piece goes back. Fired at the
    * foot of the climb: the point of the climb that pays best is not settled
    * yet, and a guess there would be worth less than the rocket.
    */
   private void fireRocket() {
      int launchStatus = Utils.firework(this.mc, false);
      if (launchStatus >= 0) {
         this.rocketArmed = false;
         this.rocketPending = false;
         this.fireworkCooldown = (Integer)this.fireworkCooldownTicks.get();
         if (launchStatus != 200) {
            this.elytraSwapSlot = launchStatus;
         }

         this.peakY = this.mc.player.getY();
         this.troughY = this.peakY;
      }
   }

   public static enum RocketMode {
      Off,
      Keep,
      Boost
   }
}
