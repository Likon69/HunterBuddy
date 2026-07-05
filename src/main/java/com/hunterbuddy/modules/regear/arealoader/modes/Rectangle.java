package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.ElytraRecast;
import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.regear.arealoader.AreaLoaderMode;
import com.hunterbuddy.modules.regear.arealoader.AreaLoaderModes;
import com.hunterbuddy.modules.regear.util.Utils;
import java.io.File;
import java.io.FileReader;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class Rectangle extends AreaLoaderMode {
   private Rectangle.PathingDataRectangle pd;
   private boolean goingToStart = true;
   private long startTime;
   private BlockPos nextWaypointTarget = null;
   private boolean recovering = false;
   private BlockPos recoveryTarget = null;
   private BlockPos lastKnownGoodPosition = null;
   private int offTrackCounter = 0;
   private static final int OFF_TRACK_THRESHOLD = 10;
   private static final int MAX_OFF_TRACK_DISTANCE = 200;
   private ElytraRecast elytraRecast = null;
   private long recoveryCompletedTime = 0L;
   private static final long RECOVERY_COOLDOWN_MS = 5000L;

   public Rectangle() {
      super(AreaLoaderModes.Rectangle);
   }

   @Override
   public void onActivate() {
      this.startTime = System.nanoTime();
      this.goingToStart = true;
      this.nextWaypointTarget = null;
      this.recovering = false;
      this.recoveryTarget = null;
      this.lastKnownGoodPosition = null;
      this.offTrackCounter = 0;
      this.elytraRecast = (ElytraRecast)Modules.get().get(ElytraRecast.class);
      this.recoveryCompletedTime = 0L;
      File file = this.getJsonFile(super.toString());
      if (file == null) {
         this.debugInfo("Error: Cannot create save file path. Check save-name setting.");
         BlockPos playerPos = this.mc.player.getBlockPos();
         BlockPos startPos = (BlockPos)this.searchArea.startPos.get();
         BlockPos targetPos = (BlockPos)this.searchArea.targetPos.get();
         if (startPos.equals(new BlockPos(0, 0, 0))) {
            startPos = playerPos;
         }

         if (targetPos.equals(new BlockPos(0, 0, 0))) {
            targetPos = startPos.add(10000, 0, 10000);
         }

         float initialYaw = startPos.getX() < targetPos.getX() ? -90.0F : 90.0F;
         this.pd = new Rectangle.PathingDataRectangle(startPos, targetPos, playerPos, initialYaw, true, playerPos.getZ());
         this.goingToStart = false;
      } else if (!file.exists()) {
         BlockPos playerPos = this.mc.player.getBlockPos();
         BlockPos startPos = (BlockPos)this.searchArea.startPos.get();
         BlockPos targetPos = (BlockPos)this.searchArea.targetPos.get();
         if (startPos.equals(new BlockPos(0, 0, 0))) {
            startPos = playerPos;
            this.debugInfo("Start position not configured, using player position: " + playerPos.toShortString());
         }

         if (targetPos.equals(new BlockPos(0, 0, 0))) {
            targetPos = startPos.add(10000, 0, 10000);
            this.debugInfo("End position not configured, using default offset: " + targetPos.toShortString());
         }

         float initialYaw;
         if (startPos.getX() < targetPos.getX()) {
            initialYaw = -90.0F;
         } else {
            initialYaw = 90.0F;
         }

         this.pd = new Rectangle.PathingDataRectangle(startPos, targetPos, playerPos, initialYaw, true, playerPos.getZ());
         this.goingToStart = false;
         this.debugInfo(
            "Rectangle started from player position "
               + playerPos.toShortString()
               + ". Will scan from "
               + startPos.toShortString()
               + " to "
               + targetPos.toShortString()
         );
      } else {
         try {
            this.debugInfo("Loading save file: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
            FileReader reader = new FileReader(file);
            this.pd = (Rectangle.PathingDataRectangle)GSON.fromJson(reader, Rectangle.PathingDataRectangle.class);
            reader.close();
            if (!this.isRectanglePathingDataValid(this.pd)) {
               ChatUtils.error("Rectangle save file is missing required fields. Disabling to prevent data loss.", new Object[0]);
               this.pd = null;
               this.disable();
               return;
            }

            this.debugInfo("Loaded saved Rectangle successfully. Current position: " + this.pd.currPos.toShortString());
            this.printRectangleEstimate();
         } catch (Exception e) {
            this.debugInfo("Failed to load saved Rectangle data. Starting fresh.");
            BlockPos playerPos = this.mc.player.getBlockPos();
            BlockPos startPos = (BlockPos)this.searchArea.startPos.get();
            BlockPos targetPos = (BlockPos)this.searchArea.targetPos.get();
            if (startPos.equals(new BlockPos(0, 0, 0))) {
               startPos = playerPos;
            }

            if (targetPos.equals(new BlockPos(0, 0, 0))) {
               targetPos = startPos.add(10000, 0, 10000);
            }

            float initialYaw = startPos.getX() < targetPos.getX() ? -90.0F : 90.0F;
            this.pd = new Rectangle.PathingDataRectangle(startPos, targetPos, playerPos, initialYaw, true, playerPos.getZ());
            this.goingToStart = false;
         }
      }

      this.initializeFlightModes();
   }

   @Override
   public void onDeactivate() {
      super.onDeactivate();
      super.saveToJson(this.goingToStart, this.pd);
      this.nextWaypointTarget = null;
      this.recoveryTarget = null;
      this.recovering = false;
   }

   private boolean isRectanglePathingDataValid(Rectangle.PathingDataRectangle data) {
      return data != null && data.currPos != null && data.initialPos != null && data.targetPos != null;
   }

   @Override
   public void resetState() {
      this.pd = null;
      this.goingToStart = true;
      this.nextWaypointTarget = null;
      this.recovering = false;
      this.recoveryTarget = null;
      this.lastKnownGoodPosition = null;
      this.offTrackCounter = 0;
      this.debugInfo("Rectangle state reset. Next activation will start fresh.");
   }

   private void printRectangleEstimate() {
      double speedBPS = (Double)this.searchArea.boatFlySpeed.get();
      double rowDistance = Math.abs(this.pd.initialPos.getX() - this.pd.targetPos.getX());
      int blockGap = 16 * (Integer)this.searchArea.rowGap.get();
      int remainingZDistance = Math.abs(this.pd.currPos.getZ() - this.pd.targetPos.getZ());
      int rowCount = remainingZDistance / blockGap;
      double totalBlocks = rowCount * (rowDistance + blockGap);
      long totalSeconds = (long)(totalBlocks / speedBPS);
      long hours = totalSeconds / 3600L;
      long minutes = totalSeconds % 3600L / 60L;
      long seconds = totalSeconds % 60L;
      this.debugInfo(
         "Completion will take an estimated %02d hours %02d minutes %02d seconds with boatfly at a speed of %.2f and a gap of %d chunks between paths.",
         hours,
         minutes,
         seconds,
         speedBPS,
         this.searchArea.rowGap.get()
      );
   }

   @Override
   public void onTick() {
      super.onTick();
      if (this.mc.player != null && this.mc.world != null) {
         if (System.nanoTime() - this.startTime > 6.0E11) {
            this.startTime = System.nanoTime();
            super.saveToJson(this.goingToStart, this.pd);
         }

         if (this.isInNether) {
            this.onTickNether();
         } else {
            this.onTickOverworld();
         }
      }
   }

   private void onTickOverworld() {
      if (this.goingToStart) {
         this.updateGoalWaypoint(this.pd.currPos);
         if (Math.sqrt(
               this.mc
                  .player
                  .getBlockPos()
                  .getSquaredDistance(this.pd.currPos.getX(), this.mc.player.getY(), this.pd.currPos.getZ())
            )
            < 5.0) {
            this.goingToStart = false;
            this.mc.player.setVelocity(0.0, 0.0, 0.0);
         } else {
            this.steerYawTowards(this.pd.currPos.toCenterPos());
            Utils.setPressed(this.mc.options.forwardKey, true);
         }
      } else {
         Utils.setPressed(this.mc.options.forwardKey, true);
         this.steerYaw(this.pd.yawDirection);
         if (Math.sqrt(
               this.mc
                  .player
                  .getBlockPos()
                  .getSquaredDistance(this.pd.targetPos.getX(), this.mc.player.getY(), this.pd.targetPos.getZ())
            )
            < 20.0) {
            this.onComplete();
         } else if (this.pd.mainPath
            && (
               this.pd.yawDirection == -90.0F
                     && this.mc.player.getX() >= Math.max(this.pd.initialPos.getX(), this.pd.targetPos.getX())
                  || this.pd.yawDirection == 90.0F
                     && this.mc.player.getX() <= Math.min(this.pd.initialPos.getX(), this.pd.targetPos.getX())
            )) {
            this.pd.yawDirection = this.mc.player.getZ() < this.pd.targetPos.getZ() ? 0.0F : 180.0F;
            this.pd.mainPath = false;
            this.mc.player.setVelocity(0.0, 0.0, 0.0);
         } else if (!this.pd.mainPath && Math.abs(this.mc.player.getZ() - this.pd.lastCompleteRowZ) >= 16 * (Integer)this.searchArea.rowGap.get()) {
            this.pd.lastCompleteRowZ = (int)this.mc.player.getZ();
            this.pd.yawDirection = this.pd.initialPos.getX() > this.mc.player.getX() ? -90.0F : 90.0F;
            this.pd.mainPath = true;
            this.mc.player.setVelocity(0.0, 0.0, 0.0);
         }
      }
   }

   private void onTickNether() {
      if (this.searchArea.netherPathMode.get() != AreaLoader.NetherPathMode.BARITONE_ELYTRA) {
         this.steerYaw(this.pd.yawDirection);
      } else {
         int blockGap = 16 * (Integer)this.searchArea.rowGap.get();
         int reachDist = (Integer)this.searchArea.netherWaypointReachDistance.get();
         if (this.recovering && this.recoveryTarget != null) {
            double distToRecovery = Math.sqrt(
               this.mc
                  .player
                  .getBlockPos()
                  .getSquaredDistance(this.recoveryTarget.getX(), this.mc.player.getY(), this.recoveryTarget.getZ())
            );
            boolean elytraRecovering = this.elytraRecast != null && this.elytraRecast.isActive() && this.elytraRecast.isRecovering();
            if (elytraRecovering) {
               this.debugInfo("Rectangle: Waiting for ElytraRecast recovery to complete...");
            } else {
               if (distToRecovery < reachDist) {
                  this.debugInfo(
                     "Rectangle: Recovery complete. Resuming from X=%d Z=%d", this.recoveryTarget.getX(), this.recoveryTarget.getZ()
                  );
                  this.recovering = false;
                  this.recoveryTarget = null;
                  this.offTrackCounter = 0;
                  this.recoveryCompletedTime = System.currentTimeMillis();
                  this.lastKnownGoodPosition = this.mc.player.getBlockPos();
                  this.nextWaypointTarget = null;
               } else if (this.needsNewGoal()) {
                  this.setBaritoneGoal(this.recoveryTarget);
               }
            }
         } else if (this.goingToStart) {
            double distToSaved = Math.sqrt(
               this.mc
                  .player
                  .getBlockPos()
                  .getSquaredDistance(this.pd.currPos.getX(), this.mc.player.getY(), this.pd.currPos.getZ())
            );
            if (distToSaved < reachDist) {
               this.goingToStart = false;
               this.lastKnownGoodPosition = this.pd.currPos;
               this.nextWaypointTarget = null;
               this.debugInfo(
                  "Rectangle: Reached saved position. Resuming at X=%d Z=%d, yaw=%.1f, mainPath=%b",
                  this.pd.currPos.getX(),
                  this.pd.currPos.getZ(),
                  this.pd.yawDirection,
                  this.pd.mainPath
               );
            } else if (this.needsNewGoal()) {
               this.setBaritoneGoal(this.pd.currPos);
            }
         } else {
            double distToTarget = Math.sqrt(
               this.mc
                  .player
                  .getBlockPos()
                  .getSquaredDistance(this.pd.targetPos.getX(), this.mc.player.getY(), this.pd.targetPos.getZ())
            );
            if (distToTarget < reachDist) {
               this.onComplete();
            } else if (this.nextWaypointTarget == null) {
               this.calculateNextWaypoint();
               if (this.nextWaypointTarget != null) {
                  this.debugInfo(
                     "Rectangle: New waypoint at X=%d Z=%d (mainPath=%b, yaw=%.1f)",
                     this.nextWaypointTarget.getX(),
                     this.nextWaypointTarget.getZ(),
                     this.pd.mainPath,
                     this.pd.yawDirection
                  );
                  this.setBaritoneGoal(this.nextWaypointTarget);
               }
            } else {
               boolean elytraRecovering = this.elytraRecast != null && this.elytraRecast.isActive() && this.elytraRecast.isRecovering();
               if (!elytraRecovering) {
                  long timeSinceRecovery = System.currentTimeMillis() - this.recoveryCompletedTime;
                  if (this.recoveryCompletedTime > 0L && timeSinceRecovery < 5000L) {
                     if (this.needsNewGoal()) {
                        this.setBaritoneGoal(this.nextWaypointTarget);
                     }
                  } else {
                     double offTrackDist = this.getRectangleOffTrackDistance();
                     if (offTrackDist > 200.0) {
                        this.offTrackCounter++;
                        if (this.offTrackCounter >= 10) {
                           this.debugInfo("Rectangle: Off-track by %.0f blocks. Saving state and initiating recovery...", offTrackDist);
                           BlockPos currentPos = this.mc.player.getBlockPos();
                           double distToLastGood = this.lastKnownGoodPosition != null
                              ? Math.sqrt(currentPos.getSquaredDistance(this.lastKnownGoodPosition))
                              : Double.MAX_VALUE;
                           if (this.lastKnownGoodPosition != null && distToLastGood > reachDist * 2) {
                              this.pd.currPos = this.lastKnownGoodPosition;
                              super.saveToJson(false, this.pd);
                              this.recoveryTarget = this.lastKnownGoodPosition;
                              ChatUtils.info(
                                 "Rectangle: Saved recovery point at X=%d Z=%d. Navigating back.",
                                 new Object[]{this.lastKnownGoodPosition.getX(), this.lastKnownGoodPosition.getZ()}
                              );
                           } else {
                              this.recoveryTarget = this.pd.currPos;
                              super.saveToJson(false, this.pd);
                              ChatUtils.info(
                                 "Rectangle: Recovering to last saved position X=%d Z=%d",
                                 new Object[]{this.pd.currPos.getX(), this.pd.currPos.getZ()}
                              );
                           }

                           this.recovering = true;
                           this.nextWaypointTarget = null;
                           this.offTrackCounter = 0;
                           this.setBaritoneGoal(this.recoveryTarget);
                           return;
                        }
                     } else {
                        this.offTrackCounter = 0;
                        this.lastKnownGoodPosition = this.mc.player.getBlockPos();
                     }

                     double distToWaypoint = Math.sqrt(
                        this.mc
                           .player
                           .getBlockPos()
                           .getSquaredDistance(this.nextWaypointTarget.getX(), this.mc.player.getY(), this.nextWaypointTarget.getZ())
                     );
                     if (distToWaypoint < reachDist) {
                        this.debugInfo(
                           "Rectangle: Reached waypoint at X=%d Z=%d", this.nextWaypointTarget.getX(), this.nextWaypointTarget.getZ()
                        );
                        if (this.pd.mainPath) {
                           int rowEndX = this.pd.yawDirection == -90.0F
                              ? Math.max(this.pd.initialPos.getX(), this.pd.targetPos.getX())
                              : Math.min(this.pd.initialPos.getX(), this.pd.targetPos.getX());
                           boolean reachedRowEnd = Math.abs(this.nextWaypointTarget.getX() - rowEndX) < reachDist;
                           if (reachedRowEnd) {
                              this.pd.yawDirection = this.nextWaypointTarget.getZ() < this.pd.targetPos.getZ() ? 0.0F : 180.0F;
                              this.pd.mainPath = false;
                              this.debugInfo("Rectangle: Reached row end, turning to yaw=%.1f", this.pd.yawDirection);
                           }
                        } else {
                           int targetRowZ = this.pd.lastCompleteRowZ + (this.pd.yawDirection == 0.0F ? blockGap : -blockGap);
                           boolean reachedNextRow = Math.abs(this.nextWaypointTarget.getZ() - targetRowZ) < reachDist;
                           if (reachedNextRow) {
                              this.pd.lastCompleteRowZ = this.nextWaypointTarget.getZ();
                              this.pd.yawDirection = this.pd.initialPos.getX() > this.nextWaypointTarget.getX() ? -90.0F : 90.0F;
                              this.pd.mainPath = true;
                              this.debugInfo("Rectangle: Reached next row at Z=%d, turning to yaw=%.1f", this.pd.lastCompleteRowZ, this.pd.yawDirection);
                           }
                        }

                        this.pd.currPos = this.nextWaypointTarget;
                        this.lastKnownGoodPosition = this.nextWaypointTarget;
                        super.saveToJson(false, this.pd);
                        this.updateGoalWaypoint(null);
                        this.nextWaypointTarget = null;
                        this.calculateNextWaypoint();
                        if (this.nextWaypointTarget != null) {
                           this.debugInfo(
                              "Rectangle: Next waypoint at X=%d Z=%d", this.nextWaypointTarget.getX(), this.nextWaypointTarget.getZ()
                           );
                           this.setBaritoneGoal(this.nextWaypointTarget);
                        }
                     } else if (this.needsNewGoal()) {
                        this.debugInfo("Rectangle: Baritone needs new goal, resetting");
                        this.setBaritoneGoal(this.nextWaypointTarget);
                     }
                  }
               }
            }
         }
      }
   }

   private void calculateNextWaypoint() {
      int blockGap = 16 * (Integer)this.searchArea.rowGap.get();
      BlockPos currentRef = this.pd.currPos != null ? this.pd.currPos : this.mc.player.getBlockPos();
      int targetX;
      int targetZ;
      if (this.pd.mainPath) {
         if (this.pd.yawDirection == -90.0F) {
            targetX = Math.max(this.pd.initialPos.getX(), this.pd.targetPos.getX());
         } else {
            targetX = Math.min(this.pd.initialPos.getX(), this.pd.targetPos.getX());
         }

         targetZ = this.pd.lastCompleteRowZ;
      } else {
         if (this.pd.yawDirection != -90.0F && this.pd.yawDirection != 90.0F) {
            targetX = this.pd.yawDirection != 0.0F && this.pd.yawDirection != 180.0F ? currentRef.getX() : currentRef.getX();
         } else {
            targetX = currentRef.getX();
         }

         if (this.pd.yawDirection == 0.0F) {
            targetZ = this.pd.lastCompleteRowZ + blockGap;
         } else {
            targetZ = this.pd.lastCompleteRowZ - blockGap;
         }
      }

      this.nextWaypointTarget = new BlockPos(targetX, this.mc.player.getBlockY(), targetZ);
      this.updateGoalWaypoint(this.nextWaypointTarget);
   }

   private void onComplete() {
      Utils.setPressed(this.mc.options.forwardKey, false);
      this.searchArea.toggle();
      if ((Boolean)this.searchArea.disconnectOnCompletion.get()) {
         AutoReconnect autoReconnect = (AutoReconnect)Modules.get().get(AutoReconnect.class);
         if (autoReconnect.isActive()) {
            autoReconnect.toggle();
         }

         this.mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[Search Area] Path is complete")));
      }
   }

   private double getRectangleOffTrackDistance() {
      if (this.pd == null) {
         return 0.0;
      } else {
         BlockPos currentPos = this.mc.player.getBlockPos();
         if (this.pd.mainPath) {
            return Math.abs(currentPos.getZ() - this.pd.lastCompleteRowZ);
         } else {
            return this.nextWaypointTarget != null ? Math.abs(currentPos.getX() - this.nextWaypointTarget.getX()) : 0.0;
         }
      }
   }

   public static class PathingDataRectangle extends AreaLoaderMode.PathingData {
      public BlockPos targetPos;
      public int lastCompleteRowZ;

      public PathingDataRectangle(BlockPos initialPos, BlockPos targetPos, BlockPos currPos, float yawDirection, boolean mainPath, int lastCompleteRowZ) {
         this.initialPos = initialPos;
         this.targetPos = targetPos;
         this.currPos = currPos;
         this.yawDirection = yawDirection;
         this.mainPath = mainPath;
         this.lastCompleteRowZ = lastCompleteRowZ;
      }
   }
}
