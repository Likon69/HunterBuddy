package com.hunterbuddy.modules.regear.util;

import com.hunterbuddy.modules.mixin.accessors.PlayerInventoryAccessor;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class ElytraTakeoff {
   public enum Phase {
      IDLE,
      COOLDOWN,
      PREPARE,
      JUMPING,
      DEPLOYING,
      BOOSTING
   }

   public interface Listener {
      void onTakeoffSuccess(double speedBps);

      void onTakeoffFailed(String reason);

      default void onTakeoffDebug(String message) {
      }
   }

   private static final int PREPARE_TICKS = 4;
   private static final int JUMP_HOLD_TICKS = 6;
   private static final int JUMP_TIMEOUT_TICKS = 40;
   private static final int MIN_AIRBORNE_TICKS = 3;
   private static final int AIR_WAIT_TICKS = 2;
   private static final int DEPLOY_TIMEOUT_TICKS = 40;
   private static final int DEPLOY_GROUND_GRACE_TICKS = 4;
   private static final int DEPLOY_RETRY_INTERVAL = 4;
   private static final int MAX_DEPLOY_ATTEMPTS = 4;
   private static final int BOOST_CONFIRM_TICKS = 8;
   private static final int RETRY_COOLDOWN_TICKS = 25;
   private static final int MAX_ATTEMPTS = 5;
   private static final double MIN_SPEED_BPS = 5.0;

   private Phase phase = Phase.IDLE;
   private int phaseTicks = 0;
   private int attempts = 0;
   private int cooldownTicks = 0;
   private int airborneTicks = 0;
   private int deployAttempts = 0;
   private int jumpHoldTicks = 0;
   private int groundTicks = 0;
   private boolean jumpedThisAttempt = false;
   private boolean boosted = false;
   private int jumpTapTicks = 0;
   private int rocketSlot = -1;
   private Listener listener;

   public boolean isActive() {
      return this.phase != Phase.IDLE;
   }

   public Phase getPhase() {
      return this.phase;
   }

   public void start(int rocketHotbarSlot, Listener listener) {
      MinecraftClient mc = MeteorClient.mc;
      if (mc.player == null) {
         return;
      }

      this.listener = listener;
      this.rocketSlot = rocketHotbarSlot;
      this.attempts = 0;
      this.cooldownTicks = 0;
      this.phase = Phase.PREPARE;
      this.phaseTicks = 0;
      this.airborneTicks = 0;
      this.deployAttempts = 0;
      this.jumpHoldTicks = 0;
      this.groundTicks = 0;
      this.jumpedThisAttempt = false;
      this.boosted = false;
      this.jumpTapTicks = 0;
      RotationUtils.getInstance().clearRotations();
      this.releaseJump();
      Utils.setPressed(mc.options.sneakKey, false);
      if (listener != null) {
         listener.onTakeoffDebug("Takeoff started");
      }
   }

   public void cancel() {
      this.releaseJump();
      this.phase = Phase.IDLE;
      this.listener = null;
   }

   public void tickPre() {
      MinecraftClient mc = MeteorClient.mc;
      if (mc.player == null || this.phase == Phase.IDLE || this.phase == Phase.COOLDOWN) {
         return;
      }

      switch (this.phase) {
         case JUMPING:
            if (mc.player.isOnGround()) {
               if (!this.jumpedThisAttempt) {
                  Utils.holdJump(mc);
                  this.jumpedThisAttempt = true;
                  this.jumpHoldTicks = JUMP_HOLD_TICKS;
                  if (this.listener != null) {
                     this.listener.onTakeoffDebug("Takeoff jump (baritone input)");
                  }
               } else if (this.jumpHoldTicks > 0) {
                  Utils.holdJump(mc);
               }
            } else {
               Utils.holdJump(mc);
            }
            break;
         case DEPLOYING:
            // A held jump never opens the wings: vanilla deploys on a fresh
            // press while falling, and that vanilla path is the one form of
            // START_FALL_FLYING the server never argues with. The taps are
            // scheduled by the deploy attempts below; between them the key
            // stays up so every press is a real edge.
            if (this.jumpTapTicks > 0) {
               this.jumpTapTicks--;
               Utils.holdJump(mc);
            } else {
               this.releaseJump();
            }
            break;
         default:
            break;
      }
   }

   public void tickPost() {
      MinecraftClient mc = MeteorClient.mc;
      if (mc.player == null || mc.world == null || this.phase == Phase.IDLE) {
         return;
      }

      if (this.phase == Phase.COOLDOWN) {
         if (this.cooldownTicks-- <= 0) {
            this.beginAttempt();
         }

         return;
      }

      this.phaseTicks++;

      switch (this.phase) {
         case PREPARE:
            if (this.phaseTicks >= PREPARE_TICKS) {
               this.beginAttempt();
            }
            break;
         case JUMPING:
            this.tickJumping(mc);
            break;
         case DEPLOYING:
            this.tickDeploying(mc);
            break;
         case BOOSTING:
            this.tickBoosting(mc);
            break;
         default:
            break;
      }

      if (this.phase == Phase.JUMPING && this.jumpHoldTicks > 0) {
         this.jumpHoldTicks--;
      }
   }

   private void beginAttempt() {
      this.phase = Phase.JUMPING;
      this.phaseTicks = 0;
      this.airborneTicks = 0;
      this.deployAttempts = 0;
      this.jumpHoldTicks = 0;
      this.groundTicks = 0;
      this.jumpedThisAttempt = false;
      this.boosted = false;
      this.jumpTapTicks = 0;
      this.attempts++;
      this.releaseJump();
      if (this.listener != null) {
         this.listener.onTakeoffDebug("Takeoff attempt " + this.attempts + "/" + MAX_ATTEMPTS);
      }
   }

   private void tickJumping(MinecraftClient mc) {
      if (mc.player.isGliding()) {
         this.enterBoosting();
         return;
      }

      if (!mc.player.isOnGround()) {
         this.airborneTicks++;
         if (this.airborneTicks >= MIN_AIRBORNE_TICKS) {
            this.phase = Phase.DEPLOYING;
            this.phaseTicks = 0;
            this.airborneTicks = 0;
            this.deployAttempts = 0;
            this.groundTicks = 0;
            if (this.listener != null) {
               this.listener.onTakeoffDebug("Airborne - deploying elytra");
            }
         }

         return;
      }

      this.airborneTicks = 0;
      if (this.jumpedThisAttempt && this.phaseTicks > JUMP_TIMEOUT_TICKS) {
         this.scheduleRetry("jump did not leave ground");
      }
   }

   private void tickDeploying(MinecraftClient mc) {
      if (mc.player.isGliding()) {
         this.enterBoosting();
         return;
      }

      if (mc.player.isOnGround()) {
         this.groundTicks++;
         if (this.phaseTicks >= DEPLOY_GROUND_GRACE_TICKS && this.groundTicks >= 2) {
            this.scheduleRetry("landed before elytra deployed");
         }
      } else {
         this.groundTicks = 0;
         this.airborneTicks++;
         if (this.airborneTicks >= AIR_WAIT_TICKS
            && (this.deployAttempts == 0 || this.airborneTicks % DEPLOY_RETRY_INTERVAL == 0)
            && this.deployAttempts < MAX_DEPLOY_ATTEMPTS) {
            this.deployElytra(mc);
            this.deployAttempts++;
            if (this.listener != null) {
               this.listener.onTakeoffDebug("Deploy attempt " + this.deployAttempts);
            }
         }
      }

      if (this.phaseTicks > DEPLOY_TIMEOUT_TICKS) {
         this.scheduleRetry("elytra deploy timed out");
      }
   }

   private void tickBoosting(MinecraftClient mc) {
      if (!mc.player.isGliding()) {
         if (this.phaseTicks > 6) {
            this.scheduleRetry("lost glide before boost completed");
         }

         return;
      }

      // On the very first tick of confirmed glide: from the ground the window
      // between wings out and feet back down is a handful of ticks, and the
      // rocket has to be inside it.
      if (!this.boosted && this.phaseTicks >= 1) {
         if (this.useFirework(mc)) {
            this.boosted = true;
            this.phaseTicks = 0;
            if (this.listener != null) {
               this.listener.onTakeoffDebug("Firework boost while gliding");
            }
         } else if (this.phaseTicks > 20) {
            this.scheduleRetry("no firework for boost");
         }

         return;
      }

      if (this.boosted) {
         double speed = this.getSpeedBps(mc);
         if (this.phaseTicks >= BOOST_CONFIRM_TICKS && (speed >= MIN_SPEED_BPS || mc.player.getVelocity().y > 0.08)) {
            this.finishSuccess(speed);
         } else if (this.phaseTicks == 14 && speed < MIN_SPEED_BPS) {
            if (this.useFirework(mc)) {
               this.phaseTicks = 0;
               if (this.listener != null) {
                  this.listener.onTakeoffDebug("Second firework boost");
               }
            }
         } else if (this.phaseTicks > 40) {
            this.scheduleRetry("boost did not reach flight speed");
         }
      }
   }

   private void enterBoosting() {
      this.phase = Phase.BOOSTING;
      this.phaseTicks = 0;
      this.boosted = false;
      this.releaseJump();
      if (this.listener != null) {
         this.listener.onTakeoffDebug("Elytra gliding");
      }
   }

   private void scheduleRetry(String reason) {
      this.releaseJump();
      if (this.attempts >= MAX_ATTEMPTS) {
         this.finishFailed(reason + " (max attempts)");
         return;
      }

      this.phase = Phase.COOLDOWN;
      this.cooldownTicks = RETRY_COOLDOWN_TICKS;
      this.phaseTicks = 0;
      if (this.listener != null) {
         this.listener.onTakeoffDebug("Retry in " + RETRY_COOLDOWN_TICKS + " ticks: " + reason);
      }
   }

   private void finishSuccess(double speedBps) {
      this.releaseJump();
      this.phase = Phase.IDLE;
      Listener current = this.listener;
      this.listener = null;
      if (current != null) {
         current.onTakeoffSuccess(speedBps);
      }
   }

   private void finishFailed(String reason) {
      this.releaseJump();
      this.phase = Phase.IDLE;
      Listener current = this.listener;
      this.listener = null;
      if (current != null) {
         current.onTakeoffFailed(reason);
      }
   }

   private void releaseJump() {
      Utils.releaseJump(MeteorClient.mc);
   }

   private void deployElytra(MinecraftClient mc) {
      // One tap of the real jump key, nothing else. The client then sends its
      // own START_FALL_FLYING with the state the server expects. The packet
      // this used to send by hand is exactly the form that gets cancelled in
      // the window after a jump — invisible from a sky-high platform where
      // the glide has room to be retried, fatal at ground level, where every
      // takeoff died within a tick of "Elytra gliding".
      this.jumpTapTicks = 1;
   }

   private boolean useFirework(MinecraftClient mc) {
      if (mc.interactionManager == null) {
         return false;
      }

      if (this.rocketSlot >= 0 && this.rocketSlot < 9) {
         ((PlayerInventoryAccessor)mc.player.getInventory()).setSelectedSlot(this.rocketSlot);
      }

      FindItemResult hotbar = InvUtils.findInHotbar(new Item[]{Items.FIREWORK_ROCKET});
      if (!hotbar.found()) {
         return false;
      }

      if (hotbar.isOffhand()) {
         mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
         mc.player.swingHand(Hand.OFF_HAND);
         return true;
      }

      if (!hotbar.isMainHand()) {
         InvUtils.swap(hotbar.slot(), true);
         mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
         mc.player.swingHand(Hand.MAIN_HAND);
         InvUtils.swapBack();
         return true;
      }

      mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
      mc.player.swingHand(Hand.MAIN_HAND);
      return true;
   }

   private double getSpeedBps(MinecraftClient mc) {
      Vec3d velocity = mc.player.getVelocity();
      return Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z) * 20.0;
   }

   public static boolean isOverSolidGround(MinecraftClient mc) {
      BlockPos below = mc.player.getBlockPos().down();
      return !mc.world.getBlockState(below).isReplaceable();
   }
}