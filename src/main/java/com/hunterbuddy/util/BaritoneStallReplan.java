package com.hunterbuddy.util;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.ICustomGoalProcess;
import baritone.api.utils.IInputOverrideHandler;
import baritone.api.utils.input.Input;
import com.hunterbuddy.HunterBuddyAddon;
import net.minecraft.client.MinecraftClient;

public final class BaritoneStallReplan {
   private final String owner;
   private final PathStallWatch watch = new PathStallWatch();

   public BaritoneStallReplan(String owner) {
      this.owner = owner;
   }

   public void tick(MinecraftClient mc) {
      if (mc.player == null || mc.interactionManager == null) return;
      IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
      ICustomGoalProcess goals = baritone.getCustomGoalProcess();
      IInputOverrideHandler input = baritone.getInputOverrideHandler();
      boolean busy = !goals.isActive()
         || !baritone.getPathingBehavior().isPathing()
         || mc.interactionManager.isBreakingBlock()
         || input.isInputForcedDown(Input.CLICK_LEFT)
         || input.isInputForcedDown(Input.CLICK_RIGHT);
      if (this.watch.tick(mc.player.getX(), mc.player.getZ(), mc.player.isOnGround(), busy)) {
         Goal goal = goals.getGoal();
         baritone.getPathingBehavior().forceCancel();
         if (goal != null) goals.setGoalAndPath(goal);
         HunterBuddyAddon.LOG.info("[" + this.owner + "] path stalled on the ground, replanning");
      }
   }

   public void reset() {
      this.watch.reset();
   }
}
