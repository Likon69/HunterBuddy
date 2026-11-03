package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.regear.arealoader.AreaLoaderModes;
import com.hunterbuddy.modules.regear.arealoader.AreaMath;
import com.hunterbuddy.modules.regear.arealoader.NetherRouteMode;
import java.io.File;
import java.io.FileReader;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.math.BlockPos;

public class Rectangle extends NetherRouteMode {
    private static final int CHUNK_SIZE = 16;
    private static final int DEFAULT_SPAN = 10000;
    private static final double TARGET_ARRIVAL = 20.0;
    private static final int SECONDS_PER_HOUR = 3600;
    private static final int SECONDS_PER_MINUTE = 60;

    private PathingDataRectangle pd;

    public Rectangle() {
        super(AreaLoaderModes.Rectangle);
    }

    private int blockGap() {
        return CHUNK_SIZE * searchArea.rowGap.get();
    }

    private RectangleLayout layout() {
        return new RectangleLayout(pd.initialPos, pd.targetPos, blockGap());
    }

    @Override
    public void onActivate() {
        super.onActivate();
        setGoingToStart(true);
        resetRoute();
        restartAutosave();
        File file = getJsonFile(toString());
        if (file != null && file.exists()) {
            loadSaved(file);
        } else {
            startFresh();
        }
        initializeFlightModes();
    }

    private void startFresh() {
        BlockPos start = resolveStart();
        BlockPos target = resolveTarget(start);
        float yaw = start.getX() < target.getX() ? RectangleYaw.EAST : RectangleYaw.WEST;
        pd = new PathingDataRectangle(start, target, mc.player.getBlockPos(), yaw, true, mc.player.getBlockZ());
        setGoingToStart(false);
        debugInfo("Rectangle started at %s heading for %s", searchArea.coords(start.getX(), start.getZ()),
            searchArea.coords(target.getX(), target.getZ()));
    }

    private BlockPos resolveStart() {
        BlockPos configured = searchArea.startPos.get();
        return BlockPos.ORIGIN.equals(configured) ? mc.player.getBlockPos() : configured;
    }

    private BlockPos resolveTarget(BlockPos start) {
        BlockPos configured = searchArea.targetPos.get();
        return BlockPos.ORIGIN.equals(configured) ? start.add(DEFAULT_SPAN, 0, DEFAULT_SPAN) : configured;
    }

    private void loadSaved(File file) {
        PathingDataRectangle loaded;
        try (FileReader reader = new FileReader(file)) {
            loaded = GSON.fromJson(reader, PathingDataRectangle.class);
        } catch (Exception e) {
            startFresh();
            return;
        }
        if (loaded == null || loaded.currPos == null || loaded.initialPos == null || loaded.targetPos == null) {
            rejectSavedFile(file);
            return;
        }
        pd = loaded;
        setGoingToStart(true);
        debugInfo("Rectangle progress loaded, estimated time left: %s", estimateTimeLeft());
    }

    private void rejectSavedFile(File file) {
        ChatUtils.error("Rectangle save file %s is incomplete, it was left untouched and the module is disabled", file.getAbsolutePath());
        pd = null;
        disable();
    }

    private String estimateTimeLeft() {
        long seconds = (long) layout().estimateSeconds(pd.currPos.getZ(), searchArea.boatFlySpeed.get());
        long hours = seconds / SECONDS_PER_HOUR;
        long minutes = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        return hours + "h " + minutes + "m " + seconds % SECONDS_PER_MINUTE + "s";
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        saveToJson(isGoingToStart(), pd);
        resetRoute();
    }

    @Override
    public void resetState() {
        pd = null;
        setGoingToStart(true);
        resetRoute();
        debugInfo("Rectangle state reset, the next activation starts fresh");
    }

    @Override
    protected PathingData pathData() {
        return pd;
    }

    @Override
    protected void tickOverworld() {
        if (isReturningToSavedPosition()) return;
        holdForward();
        steerYaw(pd.yawDirection);
        if (isWithinOfTarget(TARGET_ARRIVAL)) {
            completeRoute();
        } else if (pd.mainPath) {
            turnAtRowEnd();
        } else {
            startNextRowOnceGapCrossed();
        }
    }

    private boolean isWithinOfTarget(double distance) {
        return AreaMath.horizontalDistance(pd.targetPos, mc.player.getX(), mc.player.getZ()) < distance;
    }

    private void turnAtRowEnd() {
        if (!layout().hasPassedRowEnd(pd.yawDirection, mc.player.getX())) return;
        pd.yawDirection = layout().yawAfterRowEnd(mc.player.getZ());
        pd.mainPath = false;
        stopForTurn();
    }

    private void startNextRowOnceGapCrossed() {
        if (Math.abs(mc.player.getZ() - pd.lastCompleteRowZ) < blockGap()) return;
        pd.lastCompleteRowZ = (int) mc.player.getZ();
        pd.yawDirection = layout().yawForNextRow(mc.player.getX());
        pd.mainPath = true;
        stopForTurn();
    }

    @Override
    protected boolean finishRouteIfReached(int reach) {
        if (!isWithin(pd.targetPos, reach)) return false;
        completeRoute();
        return true;
    }

    private void completeRoute() {
        releaseForward();
        searchArea.toggle();
        if (searchArea.disconnectOnCompletion.get()) {
            PathCompletion.disconnect();
        }
    }

    @Override
    protected BlockPos computeWaypoint() {
        int y = mc.player.getBlockY();
        if (pd.mainPath) return new BlockPos(layout().rowEndX(pd.yawDirection), y, pd.lastCompleteRowZ);
        return new BlockPos(referenceX(), y, layout().nextRowZ(pd.yawDirection, pd.lastCompleteRowZ));
    }

    private int referenceX() {
        return pd.currPos != null ? pd.currPos.getX() : mc.player.getBlockX();
    }

    @Override
    protected double offTrackDistance() {
        if (pd.mainPath) return Math.abs(mc.player.getZ() - pd.lastCompleteRowZ);
        return Math.abs(mc.player.getX() - nextWaypoint().getX());
    }

    @Override
    protected void turnAt(BlockPos waypoint) {
        if (pd.mainPath) {
            turnAtRowEndWaypoint(waypoint);
        } else {
            startNextRowAtWaypoint(waypoint);
        }
    }

    private void turnAtRowEndWaypoint(BlockPos waypoint) {
        int rowEnd = layout().rowEndX(pd.yawDirection);
        if (Math.abs(waypoint.getX() - rowEnd) >= searchArea.netherWaypointReachDistance.get()) return;
        pd.yawDirection = layout().yawAfterRowEnd(waypoint.getZ());
        pd.mainPath = false;
    }

    private void startNextRowAtWaypoint(BlockPos waypoint) {
        int nextRowZ = layout().nextRowZ(pd.yawDirection, pd.lastCompleteRowZ);
        if (Math.abs(waypoint.getZ() - nextRowZ) >= searchArea.netherWaypointReachDistance.get()) return;
        pd.lastCompleteRowZ = waypoint.getZ();
        pd.yawDirection = layout().yawForNextRow(waypoint.getX());
        pd.mainPath = true;
    }

    @Override
    public CoveragePreview coveragePreview() {
        if (pd == null) return null;
        RectanglePreview preview = new RectanglePreview(pd, blockGap());
        if (!preview.isComplete()) return null;
        double x = mc.player != null ? mc.player.getX() : pd.currPos.getX();
        double z = mc.player != null ? mc.player.getZ() : pd.currPos.getZ();
        return preview.build(x, z, distanceToNextWaypoint(), isRecovering());
    }

    public static class PathingDataRectangle extends PathingData {
        public BlockPos targetPos;
        public int lastCompleteRowZ;

        public PathingDataRectangle(BlockPos initialPos, BlockPos targetPos, BlockPos currPos, float yawDirection,
                                    boolean mainPath, int lastCompleteRowZ) {
            this.initialPos = initialPos;
            this.targetPos = targetPos;
            this.currPos = currPos;
            this.yawDirection = yawDirection;
            this.mainPath = mainPath;
            this.lastCompleteRowZ = lastCompleteRowZ;
        }
    }
}
