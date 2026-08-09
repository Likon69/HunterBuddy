package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.regear.arealoader.AreaLoaderMode;
import com.hunterbuddy.modules.regear.arealoader.AreaLoaderModes;
import com.hunterbuddy.modules.regear.util.Utils;
import java.io.File;
import java.io.FileReader;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.math.BlockPos;

/**
 * Polar (Archimedean) spiral area loader, ported from the
 * "polar-spiral-efly" reference (lol.tilley.PolarSpiralEflyModule).
 *
 * The spiral uses the polar form   r = scale * theta   (scale = radius * 16),
 * so each full orbit the flight radius grows by 2*PI*scale blocks. This loads
 * chunks in an expanding circular spiral from the chosen center, exactly like
 * the reference module. The player simply steers toward the current spiral
 * goal (overworld) or sets it as a Baritone elytra goal (Nether).
 */
public class Circle extends AreaLoaderMode {
   private Circle.PathingDataCircle pd;
   private boolean goingToStart = true;
   private long startTime;
   private BlockPos nextSpiralTarget = null;
   private static final int REACH_DISTANCE_OVERWORLD = 1;
   private static final int TELEPORT_THRESHOLD = 100;
   private boolean teleportPaused = false;
   private BlockPos lastTickPos = null;

   public Circle() {
      super(AreaLoaderModes.Circle);
   }

   private int flip() {
      return ((Circle.SpiralDirection)this.searchArea.circleDirection.get()) == Circle.SpiralDirection.COUNTERCLOCKWISE ? 1 : -1;
   }

   private double scale() {
      return ((Number)this.searchArea.circleLayerRadius.get()).doubleValue() * 16.0;
   }

   private double radiansPerStep() {
      return (2.0 * Math.PI) / ((Number)this.searchArea.circleSteps.get()).doubleValue();
   }

   /** Compute the spiral point for a given angle (reference polar form). */
   private BlockPos spiralPoint(double angle) {
      double s = this.scale();
      int flip = this.flip();
      double x = this.pd.centerX + s * angle * (double)flip * Math.cos(angle);
      double z = this.pd.centerZ + s * angle * Math.sin(angle);
      return new BlockPos((int)Math.round(x), this.mc.player.getBlockY(), (int)Math.round(z));
   }

   @Override
   public void onActivate() {
      this.startTime = System.nanoTime();
      this.goingToStart = true;
      this.nextSpiralTarget = null;
      this.teleportPaused = false;
      this.lastTickPos = null;
      File file = this.getJsonFile(super.toString());
      if (file == null) {
         this.debugInfo("Error: Cannot create save file path. Check save-name setting.");
         this.startFreshFromPlayer();
      } else if (!file.exists()) {
         this.startFreshFromPlayer();
         this.debugInfo(
            "Polar spiral started: center=%s, scale=%.0f, steps/orbit=%.0f, dir=%s",
            this.searchArea.coords(this.pd.centerX, this.pd.centerZ),
            this.scale(),
            ((Number)this.searchArea.circleSteps.get()).doubleValue(),
            this.searchArea.circleDirection.get()
         );
      } else {
         try {
            this.debugInfo("Loading save file: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
            FileReader reader = new FileReader(file);
            this.pd = (Circle.PathingDataCircle)GSON.fromJson(reader, Circle.PathingDataCircle.class);
            reader.close();
            if (this.pd == null || !this.isPathingDataValid(this.pd)) {
               ChatUtils.error("Polar spiral save file is corrupted. Starting fresh.", new Object[0]);
               this.startFreshFromPlayer();
            } else {
               this.goingToStart = true;
               this.debugInfo(
                  "Loaded polar spiral state: center=%s, angle=%.4f rad (%.1f deg)",
                  this.searchArea.coords(this.pd.centerX, this.pd.centerZ),
                  this.pd.currentAngle,
                  Math.toDegrees(this.pd.currentAngle)
               );
            }
         } catch (Exception e) {
            ChatUtils.error("Failed to load polar spiral save: " + e.getMessage(), new Object[0]);
            this.startFreshFromPlayer();
         }
      }

      this.initializeFlightModes();
   }

   /** Start a fresh spiral from the player position: snap angle to the nearest
    *  spiral step on a ray from the configured center through the player,
    *  exactly like the reference onEnable(). */
   private void startFreshFromPlayer() {
      BlockPos configured = (BlockPos)this.searchArea.circleCenter.get();
      int cx = configured.getX();
      int cz = configured.getZ();

      // An unset centre means "start here", not "start at spawn". The spiral is
      // r = scale * angle, so a centre thousands of blocks away puts the first
      // goal at that same radius: with the default 64 steps per orbit, each leg
      // is 2*PI*radius/64 blocks long and turns only 5.6 degrees. At 5000 blocks
      // out that is a 490-block leg — geometrically a spiral, visually a straight
      // line — and past the loop's 1000-winding cap the goal lands hundreds of
      // thousands of blocks away. The reference has a Reset Center button for
      // exactly this; defaulting to the player is the same thing without a click.
      //
      // Deliberately not written back into the setting: the centre lives in the
      // pathing data instead. Writing it would refill the field the moment the
      // module starts, which makes the reset button next to it look broken —
      // you clear it, it comes back. Left empty, the field stays a pure user
      // override, and "Set Here" is there for whoever wants to pin one.
      if (cx == 0 && cz == 0) {
         cx = this.mc.player.getBlockX();
         cz = this.mc.player.getBlockZ();
         ChatUtils.info("Polar spiral centre set to your position: %s", this.searchArea.coords(cx, cz));
      }

      double playerX = this.mc.player.getX();
      double playerZ = this.mc.player.getZ();
      double rps = this.radiansPerStep();
      double flip = (double)this.flip();

      // Player angle relative to center.
      double playerAngle = Math.atan2(playerZ - (double)cz, playerX - (double)cx);
      double nextAngle = Math.ceil(playerAngle / rps) * rps;
      double prevDiffSq = Double.MAX_VALUE;
      double bestAngle = nextAngle;

      // Walk forward along the spiral (in whole-orbit increments) until we pass
      // the closest point to the player. This mirrors the reference onEnable().
      for (int i = 0; i < 1000; i++) {
         double currentAngle = nextAngle + (double)i * (2.0 * Math.PI);
         double spiralX = (double)cx + this.scale() * currentAngle * flip * Math.cos(currentAngle);
         double spiralZ = (double)cz + this.scale() * currentAngle * Math.sin(currentAngle);
         double dx = playerX - spiralX;
         double dz = playerZ - spiralZ;
         double diffSq = dx * dx + dz * dz;
         if (diffSq > prevDiffSq) break;
         bestAngle = currentAngle;
         prevDiffSq = diffSq;
      }

      this.pd = new Circle.PathingDataCircle(cx, cz, bestAngle);
      this.goingToStart = false;
   }

   private boolean isPathingDataValid(Circle.PathingDataCircle data) {
      // center can legitimately be (0,0); we only require the angle to be a finite number.
      return Double.isFinite(data.currentAngle);
   }

   @Override
   public void onDeactivate() {
      super.onDeactivate();
      super.saveToJson(this.goingToStart, this.pd);
      this.nextSpiralTarget = null;
   }

   @Override
   public void resetState() {
      this.pd = null;
      this.goingToStart = true;
      this.nextSpiralTarget = null;
      this.teleportPaused = false;
      this.lastTickPos = null;
      this.debugInfo("Polar spiral state reset. Next activation will start fresh.");
   }

   @Override
   public void onTick() {
      super.onTick();
      if (this.mc.player == null || this.mc.world == null || this.pd == null) {
         return;
      }

      BlockPos currentPos = this.mc.player.getBlockPos();
      if (this.lastTickPos != null && !this.goingToStart && !this.teleportPaused) {
         double tickDistance = Math.sqrt(this.lastTickPos.getSquaredDistance(currentPos));
         if (tickDistance > (double)TELEPORT_THRESHOLD) {
            this.debugInfo("TELEPORT DETECTED! Moved %.0f blocks in one tick. Pausing polar spiral.", tickDistance);
            this.lastTickPos = currentPos;
            this.teleportPaused = true;
            Utils.setPressed(this.mc.options.forwardKey, false);
            this.mc.player.setVelocity(0.0, 0.0, 0.0);
            ChatUtils.info("Polar spiral PAUSED due to teleportation.", new Object[0]);
            ChatUtils.info("Disable and re-enable the module to resume.", new Object[0]);
            return;
         }
      }
      this.lastTickPos = currentPos;
      if (this.teleportPaused) {
         Utils.setPressed(this.mc.options.forwardKey, false);
         return;
      }

      if (System.nanoTime() - this.startTime > 6.0E11) {
         this.startTime = System.nanoTime();
         super.saveToJson(this.goingToStart, this.pd);
      }

      if (System.nanoTime() < this.paused) {
         Utils.setPressed(this.mc.options.forwardKey, false);
         return;
      }

      if (this.isInNether) {
         this.onTickNether();
      } else {
         this.onTickOverworld();
      }
   }

   private void onTickOverworld() {
      double playerX = this.mc.player.getX();
      double playerZ = this.mc.player.getZ();
      double deltaX = playerX - this.pd.goalX;
      double deltaZ = playerZ - this.pd.goalZ;

      // Reference logic: only advance the spiral goal once we are within ~1 block
      // of the current goal.
      if (this.pd.goalX != 0.0 || this.pd.goalZ != 0.0) {
         if (deltaX * deltaX + deltaZ * deltaZ < 1.0) {
            this.pd.currentAngle += this.radiansPerStep();
            this.updateGoalFromAngle();
            this.debugInfo("Polar spiral: advanced to angle=%.2f deg, goal=%s", Math.toDegrees(this.pd.currentAngle), this.searchArea.coords(this.pd.goalX, this.pd.goalZ));
         }
      } else {
         // First tick: seed the goal.
         this.updateGoalFromAngle();
      }

      BlockPos targetBlock = new BlockPos((int)Math.round(this.pd.goalX), this.mc.player.getBlockY(), (int)Math.round(this.pd.goalZ));
      this.updateGoalWaypoint(targetBlock);
      // Steer toward the goal (reference does yaw = atan2(...) - 90).
      double yaw = Math.toDegrees(Math.atan2(this.pd.goalZ - playerZ, this.pd.goalX - playerX)) - 90.0;
      this.steerYaw((float)yaw);
      Utils.setPressed(this.mc.options.forwardKey, true);
   }

   private void updateGoalFromAngle() {
      double s = this.scale();
      int flip = this.flip();
      this.pd.goalX = this.pd.centerX + s * this.pd.currentAngle * (double)flip * Math.cos(this.pd.currentAngle);
      this.pd.goalZ = this.pd.centerZ + s * this.pd.currentAngle * Math.sin(this.pd.currentAngle);
   }

   private void onTickNether() {
      if (this.searchArea.netherPathMode.get() != AreaLoader.NetherPathMode.BARITONE_ELYTRA) {
         // No baritone: fall back to manual steering (same as overworld).
         this.onTickOverworld();
         return;
      }

      int reachDist = ((Integer)this.searchArea.netherWaypointReachDistance.get()).intValue();
      if (this.goingToStart) {
         double distToSavedGoal = Math.sqrt(
            this.mc.player.getBlockPos().getSquaredDistance((double)this.pd.centerX, this.mc.player.getY(), (double)this.pd.centerZ)
         );
         // Re-seed spiral goal then resume.
         this.updateGoalFromAngle();
         this.nextSpiralTarget = new BlockPos((int)Math.round(this.pd.goalX), this.mc.player.getBlockY(), (int)Math.round(this.pd.goalZ));
         double distToTarget = Math.sqrt(
            this.mc.player.getBlockPos().getSquaredDistance(this.nextSpiralTarget.getX(), this.mc.player.getY(), this.nextSpiralTarget.getZ())
         );
         if (distToTarget < (double)reachDist) {
            this.goingToStart = false;
            this.debugInfo("Polar spiral: reached saved goal, resuming normal advance.");
         } else if (this.needsNewGoal()) {
            this.setBaritoneGoal(this.nextSpiralTarget);
         }
         return;
      }

      if (this.nextSpiralTarget == null || this.needsNewGoal()) {
         double playerX = this.mc.player.getX();
         double playerZ = this.mc.player.getZ();
         double deltaX = playerX - this.pd.goalX;
         double deltaZ = playerZ - this.pd.goalZ;
         if (deltaX * deltaX + deltaZ * deltaZ < 1.0) {
            this.pd.currentAngle += this.radiansPerStep();
            this.updateGoalFromAngle();
            this.debugInfo("Polar spiral (nether): advanced to angle=%.2f deg", Math.toDegrees(this.pd.currentAngle));
         }
         this.nextSpiralTarget = new BlockPos((int)Math.round(this.pd.goalX), this.mc.player.getBlockY(), (int)Math.round(this.pd.goalZ));
         this.setBaritoneGoal(this.nextSpiralTarget);
         return;
      }

      double distToTarget = Math.sqrt(
         this.mc.player.getBlockPos().getSquaredDistance(this.nextSpiralTarget.getX(), this.mc.player.getY(), this.nextSpiralTarget.getZ())
      );
      if (distToTarget < (double)reachDist) {
         this.pd.currentAngle += this.radiansPerStep();
         this.updateGoalFromAngle();
         this.nextSpiralTarget = new BlockPos((int)Math.round(this.pd.goalX), this.mc.player.getBlockY(), (int)Math.round(this.pd.goalZ));
         this.setBaritoneGoal(this.nextSpiralTarget);
         this.debugInfo("Polar spiral (nether): reached waypoint, advancing to angle=%.2f deg", Math.toDegrees(this.pd.currentAngle));
      }
   }

   public enum SpiralDirection {
      CLOCKWISE,
      COUNTERCLOCKWISE;
   }

   public static class PathingDataCircle extends AreaLoaderMode.PathingData {
      public int centerX;
      public int centerZ;
      public double currentAngle;
      public double goalX;
      public double goalZ;

      public PathingDataCircle(int centerX, int centerZ, double currentAngle) {
         this.centerX = centerX;
         this.centerZ = centerZ;
         this.currentAngle = currentAngle;
         this.goalX = 0.0;
         this.goalZ = 0.0;
         BlockPos center = new BlockPos(centerX, 0, centerZ);
         this.initialPos = center;
         this.currPos = center;
      }
   }
}
