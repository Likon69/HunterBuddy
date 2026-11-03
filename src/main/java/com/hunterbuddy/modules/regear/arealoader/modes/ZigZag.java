package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.regear.arealoader.AreaLoaderModes;
import com.hunterbuddy.modules.regear.arealoader.AreaMath;
import com.hunterbuddy.modules.regear.arealoader.Heading;
import com.hunterbuddy.modules.regear.arealoader.NetherRouteMode;
import java.io.File;
import java.io.FileReader;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.math.BlockPos;

public class ZigZag extends NetherRouteMode {
    private static final float QUARTER_TURN = 90.0f;
    private static final float REVERSE_TURN = 180.0f;

    private PathingDataZigZag pd;

    public ZigZag() {
        super(AreaLoaderModes.ZigZag);
    }

    private int legLength() {
        return searchArea.zigzagLegLength.get();
    }

    private int rowGap() {
        return searchArea.zigzagRowGap.get();
    }

    @Override
    public void onActivate() {
        super.onActivate();
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
        BlockPos here = mc.player.getBlockPos();
        float mainYaw = snapToCardinal(mc.player.getYaw());
        float sideYaw = Heading.normalize(mainYaw + QUARTER_TURN);
        pd = new PathingDataZigZag(here, here, mainYaw, mainYaw, sideYaw, true, true, 0);
        setGoingToStart(false);
        ChatUtils.info("ZigZag started heading %s with legs of %d blocks", Heading.of(mainYaw), legLength());
    }

    private float snapToCardinal(float yaw) {
        int quarter = Math.round(Heading.normalize(yaw) / QUARTER_TURN) % 4;
        return quarter * QUARTER_TURN;
    }

    private void loadSaved(File file) {
        try (FileReader reader = new FileReader(file)) {
            acceptSaved(GSON.fromJson(reader, PathingDataZigZag.class), file);
        } catch (Exception e) {
            startFresh();
        }
    }

    private void acceptSaved(PathingDataZigZag loaded, File file) {
        if (loaded == null || loaded.currPos == null || loaded.initialPos == null) {
            rejectSavedFile(file);
            return;
        }
        if (loaded.legStartPos == null) {
            loaded.legStartPos = loaded.currPos;
        }
        pd = loaded;
        setGoingToStart(true);
        ChatUtils.info("ZigZag progress loaded, %d legs completed", pd.legsCompleted);
    }

    @Override
    protected boolean holdsMovement() {
        return System.nanoTime() < paused;
    }

    private void rejectSavedFile(File file) {
        ChatUtils.error("ZigZag save file %s is incomplete, it was left untouched and the module is disabled", file.getAbsolutePath());
        pd = null;
        disable();
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
        debugInfo("ZigZag state reset, the next activation starts fresh");
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
        if (pd.onMainLeg) {
            turnAtEndOfMainLeg();
        } else {
            turnAtEndOfSideLeg();
        }
    }

    private double distanceFlownOnLeg() {
        return AreaMath.horizontalDistance(pd.legStartPos, mc.player.getBlockPos());
    }

    private void turnAtEndOfMainLeg() {
        if (distanceFlownOnLeg() < legLength()) return;
        pd.beginSideLeg(mc.player.getBlockPos());
        stopForTurn();
    }

    private void turnAtEndOfSideLeg() {
        if (distanceFlownOnLeg() < rowGap()) return;
        pd.beginNextMainLeg(mc.player.getBlockPos());
        stopForTurn();
    }

    @Override
    protected BlockPos computeWaypoint() {
        int distance = pd.onMainLeg ? legLength() : rowGap();
        Heading heading = Heading.of(pd.yawDirection);
        return new BlockPos(pd.legStartPos.getX() + heading.stepX() * distance, mc.player.getBlockY(),
            pd.legStartPos.getZ() + heading.stepZ() * distance);
    }

    @Override
    protected double offTrackDistance() {
        if (Heading.of(pd.yawDirection).alongX()) return Math.abs(mc.player.getZ() - pd.legStartPos.getZ());
        return Math.abs(mc.player.getX() - pd.legStartPos.getX());
    }

    @Override
    protected void turnAt(BlockPos waypoint) {
        if (pd.onMainLeg) {
            pd.beginSideLeg(waypoint);
        } else {
            pd.beginNextMainLeg(waypoint);
        }
    }

    @Override
    public CoveragePreview coveragePreview() {
        if (pd == null) return null;
        ZigZagPreview preview = new ZigZagPreview(pd, legLength(), rowGap());
        if (!preview.isComplete()) return null;
        double x = mc.player != null ? mc.player.getX() : pd.currPos.getX();
        double z = mc.player != null ? mc.player.getZ() : pd.currPos.getZ();
        return preview.build(x, z, distanceToNextWaypoint(), isRecovering());
    }

    public static class PathingDataZigZag extends PathingData {
        public float mainYaw;
        public float sideYaw;
        public boolean goingForward;
        public boolean onMainLeg;
        public int legsCompleted;
        public BlockPos legStartPos;

        public PathingDataZigZag(BlockPos initialPos, BlockPos currPos, float yawDirection, float mainYaw, float sideYaw,
                                 boolean goingForward, boolean onMainLeg, int legsCompleted) {
            this.initialPos = initialPos;
            this.currPos = currPos;
            this.yawDirection = yawDirection;
            this.mainYaw = mainYaw;
            this.sideYaw = sideYaw;
            this.goingForward = goingForward;
            this.onMainLeg = onMainLeg;
            this.legsCompleted = legsCompleted;
            this.legStartPos = currPos;
        }

        void beginSideLeg(BlockPos from) {
            yawDirection = sideYaw;
            onMainLeg = false;
            legStartPos = from;
            legsCompleted++;
        }

        void beginNextMainLeg(BlockPos from) {
            goingForward = !goingForward;
            yawDirection = goingForward ? mainYaw : Heading.normalize(mainYaw + REVERSE_TURN);
            onMainLeg = true;
            legStartPos = from;
        }
    }
}
