package com.hunterbuddy.modules.regear.arealoader;

import com.hunterbuddy.modules.ElytraRecast;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.math.BlockPos;

public abstract class NetherRouteMode extends AreaLoaderMode {
    private static final double SAVED_POSITION_ARRIVAL = 5.0;
    private static final double NO_WAYPOINT = -1.0;

    private boolean goingToStart = true;
    private BlockPos nextWaypoint;

    private final RouteRecovery recovery = new RouteRecovery();
    private final AutosaveTimer autosave = new AutosaveTimer();

    protected NetherRouteMode(AreaLoaderModes type) {
        super(type);
    }

    protected abstract PathingData pathData();

    protected abstract void tickOverworld();

    protected abstract BlockPos computeWaypoint();

    protected abstract double offTrackDistance();

    protected abstract void turnAt(BlockPos waypoint);

    protected boolean finishRouteIfReached(int reach) {
        return false;
    }

    protected boolean holdsMovement() {
        return false;
    }

    protected boolean isGoingToStart() {
        return goingToStart;
    }

    protected void setGoingToStart(boolean value) {
        goingToStart = value;
    }

    protected BlockPos nextWaypoint() {
        return nextWaypoint;
    }

    @Override
    public void onTick() {
        super.onTick();
        if (mc.player == null || mc.world == null || pathData() == null) return;
        if (autosave.isDue()) {
            saveToJson(goingToStart, pathData());
        }
        if (holdsMovement()) {
            releaseForward();
            return;
        }
        runTick();
    }

    private void runTick() {
        if (isInNether) {
            tickNether();
        } else {
            tickOverworld();
        }
    }

    protected void resetRoute() {
        nextWaypoint = null;
        recovery.reset();
    }

    protected void restartAutosave() {
        autosave.restart();
    }

    protected boolean isRecovering() {
        return recovery.isActive();
    }

    protected double distanceToNextWaypoint() {
        if (nextWaypoint == null || mc.player == null) return NO_WAYPOINT;
        return AreaMath.horizontalDistance(nextWaypoint, mc.player.getX(), mc.player.getZ());
    }

    protected boolean isReturningToSavedPosition() {
        if (!goingToStart) return false;
        if (flyTowards(pathData().currPos, SAVED_POSITION_ARRIVAL)) {
            goingToStart = false;
            stopForTurn();
        }
        return true;
    }

    protected void saveProgressAt(BlockPos position) {
        pathData().currPos = position;
        saveToJson(true, pathData());
    }

    protected boolean isWithin(BlockPos spot, int reach) {
        return AreaMath.horizontalDistance(spot, mc.player.getX(), mc.player.getZ()) < reach;
    }

    private void tickNether() {
        if (searchArea.netherPathMode.get() != AreaLoader.NetherPathMode.BARITONE_ELYTRA) {
            steerYaw(pathData().yawDirection);
            return;
        }
        int reach = searchArea.netherWaypointReachDistance.get();
        if (recovery.isActive()) {
            continueRecovery(reach);
        } else if (goingToStart) {
            returnToSavedPosition(reach);
        } else {
            followRoute(reach);
        }
    }

    private void continueRecovery(int reach) {
        if (isRecastRecovering()) {
            debugInfo("Waiting for the elytra recast to finish recovering");
        } else if (isWithin(recovery.target(), reach)) {
            recovery.finish(mc.player.getBlockPos());
            nextWaypoint = null;
        } else if (needsNewGoal()) {
            setBaritoneGoal(recovery.target());
        }
    }

    private boolean isRecastRecovering() {
        ElytraRecast recast = Modules.get().get(ElytraRecast.class);
        return recast != null && recast.isActive() && recast.isRecovering();
    }

    private void returnToSavedPosition(int reach) {
        BlockPos saved = pathData().currPos;
        if (isWithin(saved, reach)) {
            goingToStart = false;
            recovery.recordGoodPosition(saved);
            nextWaypoint = null;
        } else if (needsNewGoal()) {
            setBaritoneGoal(saved);
        }
    }

    private void followRoute(int reach) {
        if (finishRouteIfReached(reach)) return;
        if (nextWaypoint == null) {
            planNextWaypoint();
            return;
        }
        if (isRecastRecovering()) return;
        if (recovery.isCoolingDown()) {
            refreshGoal();
            return;
        }
        if (!startRecoveryIfOffTrack(reach)) {
            continueTowardsWaypoint(reach);
        }
    }

    private void continueTowardsWaypoint(int reach) {
        if (isWithin(nextWaypoint, reach)) {
            advanceFromWaypoint();
        } else {
            refreshGoal();
        }
    }

    private boolean startRecoveryIfOffTrack(int reach) {
        if (!recovery.registerOffTrack(offTrackDistance(), mc.player.getBlockPos())) return false;
        BlockPos player = mc.player.getBlockPos();
        BlockPos target = recovery.isLastGoodWorthReturningTo(player, reach) ? recovery.lastGood() : pathData().currPos;
        saveProgressAt(target);
        ChatUtils.warning("Off track, flying back to %s", searchArea.coords(target.getX(), target.getZ()));
        recovery.begin(target);
        nextWaypoint = null;
        setBaritoneGoal(target);
        return true;
    }

    private void advanceFromWaypoint() {
        BlockPos reached = nextWaypoint;
        turnAt(reached);
        saveProgressAt(reached);
        recovery.recordGoodPosition(reached);
        clearGoalWaypoint();
        planNextWaypoint();
    }

    private void planNextWaypoint() {
        nextWaypoint = computeWaypoint();
        updateGoalWaypoint(nextWaypoint);
        setBaritoneGoal(nextWaypoint);
    }

    private void refreshGoal() {
        if (needsNewGoal()) {
            setBaritoneGoal(nextWaypoint);
        }
    }
}
