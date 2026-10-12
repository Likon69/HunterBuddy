package com.hunterbuddy.modules;

import com.hunterbuddy.util.QuietClicks;
import com.hunterbuddy.util.Rockets;
import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.ElytraTakeoff;
import com.hunterbuddy.modules.regear.util.Utils;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

public class RocketFly extends Module {
   private long lastRocketUse = 0L;
   private boolean launched = false;
   private double yTarget = -1.0;
   private double obstacleTargetY = -1.0;
   private int obstacleHoldTicks;
   private boolean obstacleReturning;
   private float targetPitch = 0.0F;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> fireworkDelay = this.sgGeneral
      .add(
         new Builder().name("timed-delay").description("The delay between firework usages in milliseconds.").defaultValue(4000)
            .sliderRange(0, 10000)
            .build()
      );
   private final Setting<Boolean> obstacleLookahead = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("obstacle-lookahead")
            .description("Climb above terrain ahead while gliding.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> obstacleMargin = this.sgGeneral
      .add(
         new Builder().name("obstacle-margin")
            .description("Height to keep above terrain ahead.")
            .defaultValue(8)
            .range(2, 24)
            .sliderRange(2, 24)
            .visible(this.obstacleLookahead::get)
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
   private final Setting<Boolean> takeoffFromGround = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("takeoff-from-ground")
                  .description("Jump, open the elytra and boost into flight when enabled on the ground, instead of requiring you to already be gliding.")
               .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> takeoffDebug = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("takeoff-messages")
                  .description("Narrate every phase of the takeoff. The only way to see which step fails when a takeoff does not get you off the ground.")
               .defaultValue(false)
               .visible(this.takeoffFromGround::get)
            .build()
      );

   /**
    * Own instance, deliberately not shared.
    *
    * <p>{@link ElytraTakeoff} carries the whole state of one attempt — phase,
    * tick counters, retry budget. AutoFlyingRegear and ElytraRecast each hold
    * their own for that reason, and this follows them: a shared machine would let
    * two modules cancel each other's takeoff mid-jump.
    */
   private final ElytraTakeoff takeoff = new ElytraTakeoff();

   /**
    * Set when the machine gives up, cleared as soon as flight is regained.
    *
    * <p>Without it a failed takeoff would be retried on the very next tick,
    * forever: five attempts, fail, five more, and a jump key hammered on a player
    * who is evidently stuck under a ceiling or out of rockets.
    */
   private boolean takeoffGaveUp = false;

   /**
    * Ticks the current takeoff has been running, against {@link #TAKEOFF_TIMEOUT_TICKS}.
    *
    * <p>{@link ElytraTakeoff} normally ends by itself — five attempts, then it
    * gives up. One path does not: in the boost phase, a glide that never reaches
    * flight speed fires a rocket every fourteen ticks and resets its own counter
    * each time, so with rockets in the bag it can circle forever. Gliding into a
    * wall or inside a one-block shaft is enough to sit there. The outer ceiling is
    * what stops that from quietly emptying the stack.
    */
   private int takeoffTicks = 0;
   private static final int TAKEOFF_TIMEOUT_TICKS = 280;

   public RocketFly() {
      super(HunterBuddyAddon.HUNT_CATEGORY, "RocketFly", "Maintains a level Y-flight with fireworks and smooth pitch control.");
   }

   public void onActivate() {
      HunterBuddyAddon.LOG.info("[RocketFly] activated", new Throwable("RocketFly activation stack"));
      this.launched = false;
      this.yTarget = -1.0;
      this.obstacleTargetY = -1.0;
      this.obstacleHoldTicks = 0;
      this.obstacleReturning = false;
      this.takeoffGaveUp = false;
      this.takeoff.cancel();
      if (this.mc.player == null) {
         return;
      }

      if (this.mc.player.isGliding()) {
         return;
      }

      if (!(Boolean)this.takeoffFromGround.get()) {
         this.info("You must be flying before enabling RocketFly.", new Object[0]);
      } else if (!this.hasElytraEquipped()) {
         this.error("No elytra equipped!", new Object[0]);
      } else {
         this.startTakeoff();
      }
   }

   public void onDeactivate() {
      this.takeoff.cancel();
      Utils.setPressed(this.mc.options.jumpKey, false);
   }

   private boolean hasElytraEquipped() {
      return this.mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
   }

   private void startTakeoff() {
      this.takeoffTicks = 0;

      this.mc.player.setPitch(-75.0F);
      this.ensureRocketsInHotbar();

      this.takeoff.start(-1, new ElytraTakeoff.Listener() {
         @Override
         public void onTakeoffSuccess(double speedBps) {
            // Nothing to hand over: tickFlyLogic sees isGliding on the next tick
            // and takes the Y lock from there, exactly as if the flight had been
            // started by hand.
            RocketFly.this.takeoffGaveUp = false;
            if ((Boolean)RocketFly.this.takeoffDebug.get()) {
               RocketFly.this.info("Takeoff complete at " + String.format("%.1f", speedBps) + " b/s", new Object[0]);
            }
         }

         @Override
         public void onTakeoffFailed(String reason) {
            RocketFly.this.takeoffGaveUp = true;
            RocketFly.this.warning("Takeoff failed: " + reason, new Object[0]);
         }

         @Override
         public void onTakeoffDebug(String message) {
            if ((Boolean)RocketFly.this.takeoffDebug.get()) {
               RocketFly.this.info(message, new Object[0]);
            }
         }
      });
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

            if ((Boolean)this.obstacleLookahead.get() && this.mc.world != null && !this.mc.world.getDimension().hasCeiling()) {
               if (this.mc.player.age % 5 == 0) this.scanAhead();
            } else {
               this.obstacleTargetY = -1.0;
               this.obstacleHoldTicks = 0;
               this.obstacleReturning = false;
            }
            if (Math.abs(currentY - this.yTarget) <= 10.0) this.obstacleReturning = false;

            if (!(Boolean)this.useManualY.get() && this.obstacleTargetY <= 0.0 && !this.obstacleReturning) {
               double yDiffFromLock = currentY - this.yTarget;
               if (Math.abs(yDiffFromLock) > 10.0) {
                  this.yTarget = currentY;
                  this.info("Y-lock reset due to altitude deviation.", new Object[0]);
               }
            }

            double effTarget = this.obstacleTargetY > 0.0 && this.obstacleTargetY > this.yTarget ? this.obstacleTargetY : this.yTarget;
            double yDiff = currentY - effTarget;
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
            // Not gliding. The old path here jumped once and then fired rockets
            // on the ground — a firework only propels a player who is already
            // gliding, so it burned the stack and went nowhere. The missing step
            // was the deploy between the two: jump, open the elytra in the air,
            // then boost. ElytraTakeoff is that sequence, already written and
            // already relied on by AutoFlyingRegear and ElytraRecast.
            if ((Boolean)this.takeoffFromGround.get() && !this.takeoffGaveUp && this.hasElytraEquipped()) {
               this.startTakeoff();
            } else if (!this.launched) {
               this.mc.player.jump();
               this.launched = true;
            } else if (System.currentTimeMillis() - this.lastRocketUse > 1000L) {
               this.tryUseFirework();
            }

            this.yTarget = -1.0;
            this.obstacleTargetY = -1.0;
            this.obstacleHoldTicks = 0;
            this.obstacleReturning = false;
         }
      }
   }

   private void scanAhead() {
      Vec3d velocity = this.mc.player.getVelocity();
      double dirX = velocity.x;
      double dirZ = velocity.z;
      double speed = Math.hypot(dirX, dirZ);
      if (speed >= 0.3) {
         dirX /= speed;
         dirZ /= speed;
      } else {
         double yaw = Math.toRadians(this.mc.player.getYaw());
         dirX = -Math.sin(yaw);
         dirZ = Math.cos(yaw);
      }

      int margin = this.obstacleMargin.get();
      double currentY = this.mc.player.getY();
      double effTarget = this.obstacleTargetY > 0.0 ? Math.max(this.yTarget, this.obstacleTargetY) : this.yTarget;
      int bottomY = this.mc.world.getBottomY();
      int topY = this.mc.world.getTopYInclusive();
      int low = Math.max(bottomY, Math.min(topY, (int)Math.floor(Math.min(currentY, this.yTarget)) - margin));
      int high = Math.max(bottomY, Math.min(topY, (int)Math.ceil(Math.max(currentY, effTarget)) + margin));
      double required = this.yTarget;
      int requiredTop = 0;
      int requiredDistance = 0;
      for (int distance = 16; distance <= 192; distance += 16) {
         double px = this.mc.player.getX() + dirX * distance;
         double pz = this.mc.player.getZ() + dirZ * distance;
         int x = (int)Math.floor(px);
         int z = (int)Math.floor(pz);
         if (!this.mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) break;
         int top = this.findObstacleTop(x, z, low, high, margin);
         top = Math.max(top, this.findObstacleTop((int)Math.floor(px + dirZ * 3.0), (int)Math.floor(pz - dirX * 3.0), low, high, margin));
         top = Math.max(top, this.findObstacleTop((int)Math.floor(px - dirZ * 3.0), (int)Math.floor(pz + dirX * 3.0), low, high, margin));
         double need = top + margin;
         if (need > required) {
            required = need;
            requiredTop = top;
            requiredDistance = distance;
         }
      }

      if (required > this.yTarget) {
         boolean starting = this.obstacleTargetY == -1.0;
         this.obstacleTargetY = Math.min(Math.max(this.obstacleTargetY, required), this.mc.world.getTopYInclusive() - 10);
         this.obstacleHoldTicks = 60;
         if (starting && this.obstacleTargetY > 0.0) {
            this.info("Obstacle ahead: top=" + requiredTop + " at " + requiredDistance + "m, climbing to " + this.obstacleTargetY, new Object[0]);
            if (this.obstacleTargetY - this.mc.player.getY() > 10.0 && System.currentTimeMillis() - this.lastRocketUse > 1000L) {
               this.tryUseFirework();
            }
         }
      } else if (this.obstacleHoldTicks > 0) {
         this.obstacleHoldTicks -= 5;
         if (this.obstacleHoldTicks <= 0 && this.obstacleTargetY > 0.0) {
            if (this.mc.player.getY() > this.yTarget + 10.0) this.obstacleReturning = true;
            this.obstacleTargetY = -1.0;
            this.info("Obstacle cleared, back to " + this.yTarget, new Object[0]);
         }
      }
   }

   private int findObstacleTop(int x, int z, int low, int high, int margin) {
      int top = this.mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
      int bottomY = this.mc.world.getBottomY();
      if (top <= bottomY) return Integer.MIN_VALUE;
      if (top + margin <= this.yTarget) return top;

      final int skyGap = 32;
      int end = Math.min(high, top - 1);
      int firstBlocking = top;
      int air = 0;
      int lowerAir = 0;
      int maxAir = 0;
      boolean lowerRun = true;
      BlockPos.Mutable pos = new BlockPos.Mutable();
      for (int y = low; y <= end; y++) {
         BlockState state = this.mc.world.getBlockState(pos.set(x, y, z));
         if (state.blocksMovement() || !state.getFluidState().isEmpty()) {
            firstBlocking = y;
            break;
         }
         if (state.isAir()) {
            air++;
            if (lowerRun) lowerAir++;
         } else {
            air = 0;
            lowerRun = false;
         }
         maxAir = Math.max(maxAir, air);
      }

      if (firstBlocking == top) {
         if (maxAir >= skyGap) return Integer.MIN_VALUE;
         int downAir = lowerAir;
         for (int y = low - 1; y >= bottomY && downAir < skyGap; y--) {
            if (!this.mc.world.getBlockState(pos.set(x, y, z)).isAir()) break;
            downAir++;
         }
         if (downAir >= skyGap) return Integer.MIN_VALUE;
         if (lowerAir == end - low + 1) air += downAir - lowerAir;
         for (int y = end + 1; y < top && air < skyGap; y++) {
            if (!this.mc.world.getBlockState(pos.set(x, y, z)).isAir()) break;
            air++;
         }
         return air >= skyGap ? Integer.MIN_VALUE : top;
      }

      int lastBlocking = firstBlocking;
      air = 0;
      for (int y = firstBlocking + 1; y < top; y++) {
         BlockState state = this.mc.world.getBlockState(pos.set(x, y, z));
         if (state.blocksMovement() || !state.getFluidState().isEmpty()) {
            lastBlocking = y;
            air = 0;
         } else if (state.isAir()) {
            if (++air >= skyGap) break;
         } else {
            air = 0;
         }
      }
      return lastBlocking + 1;
   }

   public void resetYLock() {
      this.yTarget = -1.0;
      this.launched = false;
      this.obstacleTargetY = -1.0;
      this.obstacleHoldTicks = 0;
      this.obstacleReturning = false;
   }

   @EventHandler
   private void onTick(Pre event) {
      // The machine owns the jump key while it runs, so the fly logic must stay
      // out of its way — pitch steering during the jump is what drops you back
      // onto the ground before the elytra ever opens.
      if (this.takeoff.isActive()) {
         if (this.mc.player != null && !this.mc.player.isGliding()) {
            this.mc.player.setPitch(-75.0F);
         }
         this.takeoff.tickPre();
         return;
      }

      // Flight regained by any means clears the give-up latch, so losing it again
      // later gets a fresh set of attempts rather than silence.
      if (this.takeoffGaveUp && this.mc.player != null && this.mc.player.isGliding()) {
         this.takeoffGaveUp = false;
      }

      this.tickFlyLogic();
   }

   @EventHandler
   private void onTickPost(Post event) {
      if (this.mc.player != null && this.mc.world != null && this.takeoff.isActive()) {
         this.takeoff.tickPost();

         if (++this.takeoffTicks > TAKEOFF_TIMEOUT_TICKS) {
            this.takeoff.cancel();
            this.takeoffGaveUp = true;
            this.warning("Takeoff timed out.", new Object[0]);
         }
      }
   }

   private void tryUseFirework() {
      if (!this.ensureRocketsInHotbar()) {
         return;
      }

      Utils.firework(this.mc, false);
      this.lastRocketUse = System.currentTimeMillis();
   }

   /** Brings a firework stack into the hotbar if there is none there yet. */
   private boolean ensureRocketsInHotbar() {
      FindItemResult hotbar = InvUtils.findInHotbar(Rockets::isPlain);
      if (hotbar.found()) {
         return true;
      }

      FindItemResult inv = InvUtils.find(Rockets::isPlain);
      if (!inv.found()) {
         this.info("No fireworks found in hotbar or inventory.", new Object[0]);
         return false;
      }

      int hotbarSlot = this.findEmptyHotbarSlot();
      if (hotbarSlot == -1) {
         this.info("No empty hotbar slot available to move fireworks.", new Object[0]);
         return false;
      }

      if (!QuietClicks.readyOrSettle()) {
         return false;
      }

      InvUtils.move().from(inv.slot()).to(hotbarSlot);
      return true;
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
