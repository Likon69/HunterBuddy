package com.hunterbuddy.modules.regear.arealoader;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IElytraProcess;
import com.hunterbuddy.modules.regear.util.BaritoneHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

final class AreaBaritoneGoals {
    private static final long SET_COOLDOWN_MS = 2000;
    private static final long STUCK_CHECK_INTERVAL_MS = 1000;
    private static final double STUCK_MOVEMENT = 5.0;
    private static final int STUCK_THRESHOLD = 10;

    private final AreaLoader area;
    private final AreaDebug debug;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private BlockPos currentGoal;
    private boolean started;
    private long lastSetMs;
    private int stuckCounter;
    private long lastStuckCheckMs;
    private BlockPos lastStuckCheckPos;

    AreaBaritoneGoals(AreaLoader area, AreaDebug debug) {
        this.area = area;
        this.debug = debug;
    }

    void setGoal(BlockPos goal) {
        if (area.netherPathMode.get() != AreaLoader.NetherPathMode.BARITONE_ELYTRA || goal == null) return;
        if (!BaritoneHelper.hasElytraProcess()) {
            debug.info("Baritone elytra process is not available");
            return;
        }
        long now = System.currentTimeMillis();
        if (goal.equals(currentGoal) && now - lastSetMs < SET_COOLDOWN_MS) return;
        sendGoal(goal, now);
    }

    private void sendGoal(BlockPos goal, long now) {
        try {
            currentGoal = goal;
            lastSetMs = now;
            resetStuckTracking();
            BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
            BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().pathTo(new GoalXZ(goal.getX(), goal.getZ()));
            started = true;
        } catch (Exception e) {
            debug.info("Could not send the Baritone goal: %s", e.getMessage());
        }
    }

    boolean hasReachedGoal() {
        if (currentGoal == null || mc.player == null) return false;
        double distance = AreaMath.horizontalDistance(currentGoal, mc.player.getX(), mc.player.getZ());
        return distance < area.netherWaypointReachDistance.get();
    }

    boolean isIdle() {
        if (!BaritoneHelper.hasElytraProcess()) return true;
        try {
            IElytraProcess process = BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess();
            return process == null || process.currentDestination() == null;
        } catch (Exception e) {
            return true;
        }
    }

    boolean isStuck() {
        if (currentGoal == null || mc.player == null) return false;
        long now = System.currentTimeMillis();
        if (now - lastStuckCheckMs < STUCK_CHECK_INTERVAL_MS) return false;
        BlockPos position = mc.player.getBlockPos();
        boolean stuck = countStall(position);
        lastStuckCheckMs = now;
        lastStuckCheckPos = position;
        return stuck;
    }

    private boolean countStall(BlockPos position) {
        if (lastStuckCheckPos == null) return false;
        if (AreaMath.horizontalDistance(position, lastStuckCheckPos) >= STUCK_MOVEMENT) {
            stuckCounter = 0;
            return false;
        }
        stuckCounter++;
        if (stuckCounter < STUCK_THRESHOLD) return false;
        debug.info("Player has not moved for %d checks, goal is stuck", stuckCounter);
        return true;
    }

    boolean needsNewGoal() {
        if (currentGoal == null || hasReachedGoal()) return true;
        if (System.currentTimeMillis() - lastSetMs < SET_COOLDOWN_MS) return false;
        if (isIdle()) {
            debug.info("Baritone is idle, a new goal is needed");
            return true;
        }
        if (!isStuck()) return false;
        stuckCounter = 0;
        return true;
    }

    void stopIfStarted() {
        if (started) {
            BaritoneHelper.stopAllPathing();
        }
    }

    void reset() {
        currentGoal = null;
        started = false;
        lastSetMs = 0;
        resetStuckTracking();
    }

    private void resetStuckTracking() {
        stuckCounter = 0;
        lastStuckCheckMs = 0;
        lastStuckCheckPos = null;
    }
}
