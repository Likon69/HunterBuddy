package com.hunterbuddy.modules.regear.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.utils.input.Input;
import baritone.api.utils.interfaces.IGoalRenderPos;

public class BaritoneHelper {
   private static Boolean available;
   private static Boolean elytraProcess;

   public static boolean isAvailable() {
      if (available == null) {
         try {
            Class.forName("baritone.api.BaritoneAPI");
            available = true;
         } catch (ClassNotFoundException e) {
            available = false;
         }
      }

      return available;
   }

   public static boolean hasElytraProcess() {
      if (elytraProcess == null) {
         if (!isAvailable()) {
            elytraProcess = false;
         } else {
            try {
               Class.forName("baritone.api.IBaritone").getMethod("getElytraProcess");
               elytraProcess = true;
            } catch (Exception e) {
               elytraProcess = false;
            }
         }
      }

      return elytraProcess;
   }

   /**
    * Horizontal position Baritone is currently heading for, or null.
    *
    * <p>Returned in the dimension you are standing in, because that is where Baritone's goals
    * already live — unlike an Xaero waypoint, which may be stored in Overworld coordinates and
    * has to be converted. Anything applying that conversion here would be wrong by a factor of
    * eight in the Nether, on exactly the flights this is meant to describe.
    *
    * <p>Two sources, in order: the elytra process when Baritone is flying, and the custom goal
    * for a walk or a goto. Goals with no horizontal position of their own — a Y level, say —
    * give null rather than a made-up coordinate.
    */
   public static int[] getGoalXZ() {
      if (!isAvailable()) {
         return null;
      }

      try {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         if (baritone == null) {
            return null;
         }

         if (hasElytraProcess() && baritone.getElytraProcess() != null) {
            net.minecraft.util.math.BlockPos destination = baritone.getElytraProcess().currentDestination();
            if (destination != null) {
               return new int[]{destination.getX(), destination.getZ()};
            }
         }

         if (baritone.getCustomGoalProcess() == null) {
            return null;
         }

         Goal goal = baritone.getCustomGoalProcess().getGoal();
         if (goal == null) {
            return null;
         }

         if (goal instanceof GoalXZ xz) {
            return new int[]{xz.getX(), xz.getZ()};
         }

         // Covers GoalBlock, GoalNear and everything else that renders at a position, without
         // naming each one: they all expose it through this interface.
         if (goal instanceof IGoalRenderPos renderPos && renderPos.getGoalPos() != null) {
            return new int[]{renderPos.getGoalPos().getX(), renderPos.getGoalPos().getZ()};
         }

         return null;
      } catch (Exception e) {
         return null;
      }
   }

   public static void stopAllPathing() {
      if (!isAvailable()) {
         return;
      }

      try {
         IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         baritone.getPathingBehavior().cancelEverything();
         baritone.getPathingBehavior().forceCancel();
         baritone.getCustomGoalProcess().setGoal(null);
         baritone.getInputOverrideHandler().clearAllKeys();
         if (hasElytraProcess()) {
            baritone.getCommandManager().execute("forcecancel");
         }
      } catch (Exception ignored) {}
   }

   public static void setInput(Input input, boolean pressed) {
      if (!isAvailable()) {
         return;
      }

      try {
         BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler().setInputForceState(input, pressed);
      } catch (Exception ignored) {}
   }

   public static void setJumpPressed(boolean pressed) {
      setInput(Input.JUMP, pressed);
   }

   public static void clearInputs() {
      if (!isAvailable()) {
         return;
      }

      try {
         BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler().clearAllKeys();
      } catch (Exception ignored) {}
   }
}
