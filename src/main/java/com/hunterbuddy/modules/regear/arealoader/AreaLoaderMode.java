package com.hunterbuddy.modules.regear.arealoader;

import com.google.gson.Gson;
import com.hunterbuddy.modules.WaypointFollower;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.modules.regear.util.Utils;
import java.io.File;
import java.util.List;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public abstract class AreaLoaderMode {
    protected static final Gson GSON = AreaSaveFiles.GSON;

    protected final AreaLoader searchArea;
    protected final MinecraftClient mc;
    protected long paused;
    protected CurrentDimension currentDimension = CurrentDimension.UNKNOWN;
    protected boolean isInNether;
    protected boolean flightModesInitialized;

    private final AreaLoaderModes type;
    private final AreaDebug debug;
    private final AreaGoalMarker goalMarker;
    private final AreaBaritoneGoals baritoneGoals;
    private final AreaFlightModes flightModes;
    private final AreaSaveFiles saveFiles;

    public AreaLoaderMode(AreaLoaderModes type) {
        this.type = type;
        this.searchArea = Modules.get().get(AreaLoader.class);
        this.mc = MinecraftClient.getInstance();
        this.debug = new AreaDebug(searchArea);
        this.goalMarker = new AreaGoalMarker(searchArea);
        this.baritoneGoals = new AreaBaritoneGoals(searchArea, debug);
        this.flightModes = new AreaFlightModes(searchArea, debug);
        this.saveFiles = new AreaSaveFiles(searchArea, debug);
    }

    public void onTick() {
        checkDimensionChange();
    }

    public void onActivate() {
        saveFiles.currentSuffix();
        WaypointFollower follower = activeFollower();
        if (follower != null) {
            follower.setAreaLoaderSteering(true);
        }
    }

    public void onDeactivate() {
        WaypointFollower follower = activeFollower();
        if (follower != null) {
            follower.releaseAreaLoaderSteering();
        }
        cleanupFlightModes();
        clearGoalWaypoint();
        clearSteeringState();
        releaseSteering();
    }

    private WaypointFollower activeFollower() {
        WaypointFollower follower = Modules.get().get(WaypointFollower.class);
        return follower != null && follower.isActive() ? follower : null;
    }

    public abstract void resetState();

    public void disable() {
        mc.execute(() -> {
            if (searchArea.isActive()) {
                searchArea.toggle();
            }
        });
    }

    public void clear() {
        File file = saveFiles.file(type.name());
        if (file == null) {
            debugInfo("Nothing to clear, the save path is invalid");
        } else if (saveFiles.delete(file)) {
            debugInfo("Deleted %s", file.getAbsolutePath());
        }
        resetState();
    }

    public void clear(String mode) {
        File file = saveFiles.file(mode);
        if (file == null) {
            debugInfo("Nothing to clear, the save path is invalid");
            return;
        }
        if (saveFiles.delete(file)) {
            debugInfo("Deleted %s", file.getAbsolutePath());
        }
    }

    public void clearAll() {
        int deleted = saveFiles.deleteEveryFile();
        resetState();
        debugInfo("Cleared all save files, %d deleted", deleted);
    }

    @Override
    public String toString() {
        return type.name();
    }

    public CoveragePreview coveragePreview() {
        return null;
    }

    protected void debugInfo(String message) {
        debug.info(message);
    }

    protected void debugInfo(String format, Object... args) {
        debug.info(format, args);
    }

    protected void updateGoalWaypoint(BlockPos target) {
        goalMarker.update(target);
    }

    protected void clearGoalWaypoint() {
        goalMarker.clear();
    }

    protected boolean isNether() {
        return mc.world != null && mc.world.getRegistryKey() == World.NETHER;
    }

    protected boolean isEnd() {
        return mc.world != null && mc.world.getRegistryKey() == World.END;
    }

    protected boolean isSurvival() {
        return mc.interactionManager != null && mc.interactionManager.getCurrentGameMode().isSurvivalLike();
    }

    protected CurrentDimension getDimension() {
        if (mc.world == null) return CurrentDimension.UNKNOWN;
        if (isNether()) return CurrentDimension.NETHER;
        return isEnd() ? CurrentDimension.END : CurrentDimension.OVERWORLD;
    }

    protected void checkDimensionChange() {
        if (mc.world == null || mc.player == null || mc.world.getRegistryKey().getValue() == null) return;
        if (!flightModesInitialized) {
            initializeFlightModes();
            return;
        }
        CurrentDimension now = getDimension();
        if (now == currentDimension) return;
        debugInfo("Dimension changed from %s to %s", currentDimension, now);
        cleanupFlightModes();
        currentDimension = now;
        isInNether = now == CurrentDimension.NETHER;
        initializeFlightModes();
    }

    protected void initializeFlightModes() {
        if (mc.world == null || mc.player == null) {
            debugInfo("World is not ready, flight modes are not initialized");
            return;
        }
        if (!isSurvival()) {
            debugInfo("Not in survival, flight modes are not initialized");
            return;
        }
        flightModesInitialized = true;
        currentDimension = getDimension();
        isInNether = currentDimension == CurrentDimension.NETHER;
        engageFlightModes();
    }

    private void engageFlightModes() {
        if (!isInNether) {
            flightModes.engageOverworld();
        } else if (!flightModes.engageNether()) {
            disable();
        }
    }

    protected void cleanupFlightModes() {
        baritoneGoals.stopIfStarted();
        flightModes.release();
        flightModesInitialized = false;
        baritoneGoals.reset();
    }

    protected void setBaritoneGoal(BlockPos goal) {
        baritoneGoals.setGoal(goal);
    }

    protected boolean needsNewGoal() {
        return baritoneGoals.needsNewGoal();
    }

    protected void stopForTurn() {
        if (mc.player == null || mc.player.isGliding()) return;
        mc.player.setVelocity(Vec3d.ZERO);
    }

    protected void steerYaw(float yaw) {
        if (mc.player == null) return;
        mc.player.setYaw(yaw);
        if (flightModes.drivesPitch()) return;
        RotationUtils.getInstance().sustainRotation(yaw, mc.player.getPitch());
    }

    protected void steerYawTowards(Vec3d target) {
        steerYaw(RotationUtils.getYaw(target));
    }

    protected void holdForward() {
        Utils.setPressed(mc.options.forwardKey, true);
    }

    protected void releaseForward() {
        Utils.setPressed(mc.options.forwardKey, false);
    }

    protected boolean flyTowards(BlockPos spot, double arrivalDistance) {
        updateGoalWaypoint(spot);
        if (AreaMath.horizontalDistance(spot, mc.player.getX(), mc.player.getZ()) < arrivalDistance) return true;
        steerYawTowards(Vec3d.ofCenter(spot));
        holdForward();
        return false;
    }

    protected void releaseSteering() {
        GameOptions options = mc.options;
        Utils.setPressed(options.forwardKey, false);
        Utils.setPressed(options.backKey, false);
        Utils.setPressed(options.leftKey, false);
        Utils.setPressed(options.rightKey, false);
        Utils.setPressed(options.jumpKey, false);
        Utils.setPressed(options.sneakKey, false);
        RotationUtils.getInstance().clearRotations();
    }

    protected void clearSteeringState() {
        baritoneGoals.reset();
    }

    protected String getDimensionSuffix() {
        return saveFiles.currentSuffix();
    }

    protected File getJsonFile(String fileName) {
        return saveFiles.file(fileName);
    }

    protected File getJsonFileForDimension(String fileName, String suffix) {
        return saveFiles.fileForDimension(fileName, suffix);
    }

    protected void saveToJson(boolean goingToStart, PathingData data) {
        saveFiles.save(type.name(), goingToStart, data);
    }

    protected enum CurrentDimension {
        OVERWORLD,
        NETHER,
        END,
        UNKNOWN
    }

    protected static class PathingData {
        public BlockPos initialPos;
        public BlockPos currPos;
        public float yawDirection;
        public boolean mainPath;
    }

    public static final class CoveragePreview {
        public final List<double[]> points;
        public final int flownSegments;
        public final double currentX;
        public final double currentZ;
        public final String label;
        public final String detail;
        public final double nextTurnDistance;
        public final boolean recovering;
        public final int modeCounter;

        public CoveragePreview(List<double[]> points, int flownSegments, double currentX, double currentZ, String label,
                               String detail, double nextTurnDistance, boolean recovering) {
            this(points, flownSegments, currentX, currentZ, label, detail, nextTurnDistance, recovering, -1);
        }

        public CoveragePreview(List<double[]> points, int flownSegments, double currentX, double currentZ, String label,
                               String detail, double nextTurnDistance, boolean recovering, int modeCounter) {
            this.points = points;
            this.flownSegments = flownSegments;
            this.currentX = currentX;
            this.currentZ = currentZ;
            this.label = label;
            this.detail = detail;
            this.nextTurnDistance = nextTurnDistance;
            this.recovering = recovering;
            this.modeCounter = modeCounter;
        }
    }
}
