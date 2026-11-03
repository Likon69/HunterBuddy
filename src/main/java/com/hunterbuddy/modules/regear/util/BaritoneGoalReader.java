package com.hunterbuddy.modules.regear.util;

import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IElytraProcess;
import baritone.api.utils.interfaces.IGoalRenderPos;
import net.minecraft.util.math.BlockPos;

final class BaritoneGoalReader {
    private BaritoneGoalReader() {
    }

    static int[] readWithElytra(IBaritone baritone) {
        BlockPos destination = elytraDestination(baritone);
        if (destination != null) return horizontal(destination.getX(), destination.getZ());
        return customGoal(baritone);
    }

    static int[] readCustomGoal(IBaritone baritone) {
        return customGoal(baritone);
    }

    private static BlockPos elytraDestination(IBaritone baritone) {
        IElytraProcess process = baritone.getElytraProcess();
        return process == null ? null : process.currentDestination();
    }

    private static int[] customGoal(IBaritone baritone) {
        if (baritone.getCustomGoalProcess() == null) return null;
        Goal goal = baritone.getCustomGoalProcess().getGoal();
        if (goal instanceof GoalXZ goalXZ) return horizontal(goalXZ.getX(), goalXZ.getZ());
        if (goal instanceof IGoalRenderPos rendered) return renderedPosition(rendered);
        return null;
    }

    private static int[] renderedPosition(IGoalRenderPos rendered) {
        BlockPos position = rendered.getGoalPos();
        return position == null ? null : horizontal(position.getX(), position.getZ());
    }

    private static int[] horizontal(int x, int z) {
        return new int[]{x, z};
    }
}
