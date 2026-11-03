package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.regear.arealoader.AreaLoaderMode;
import com.hunterbuddy.modules.regear.arealoader.AreaLoaderModes;
import com.hunterbuddy.modules.regear.arealoader.AreaMath;
import com.hunterbuddy.modules.regear.arealoader.AutosaveTimer;
import com.hunterbuddy.modules.regear.arealoader.Heading;
import java.io.File;
import java.io.FileReader;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Spiral extends AreaLoaderMode {
    private static final int CHUNK_SIZE = 16;
    private static final float FRESH_START_YAW = -90.0f;
    private static final double SAVED_POSITION_ARRIVAL = 5.0;
    private static final double RECOVERY_ARRIVAL = 20.0;

    private PathingDataSpiral pd;
    private boolean goingToStart = true;
    private BlockPos nextCorner;
    private BlockPos recoveryTarget;
    private final AutosaveTimer autosave = new AutosaveTimer();
    private final TeleportGuard teleportGuard = new TeleportGuard();

    public Spiral() {
        super(AreaLoaderModes.Spiral);
    }

    private int blockGap() {
        return CHUNK_SIZE * searchArea.rowGap.get();
    }

    private boolean isRecovering() {
        return recoveryTarget != null;
    }

    private boolean hasUsablePath() {
        return pd != null && pd.spiralOrigin != null && pd.currPos != null && pd.initialPos != null;
    }

    @Override
    public void onActivate() {
        super.onActivate();
        goingToStart = true;
        clearTargets();
        teleportGuard.reset();
        autosave.restart();
        File file = getJsonFile(toString());
        if (file == null || !file.exists()) {
            startFresh();
        } else if (!loadSaved(file)) {
            return;
        }
        initializeFlightModes();
    }

    private void clearTargets() {
        nextCorner = null;
        recoveryTarget = null;
    }

    private void startFresh() {
        BlockPos here = mc.player.getBlockPos();
        pd = new PathingDataSpiral(here, here, FRESH_START_YAW, true, 0, 0);
        goingToStart = false;
    }

    private boolean loadSaved(File file) {
        PathingDataSpiral loaded = parse(file);
        if (loaded == null || SpiralSaveCheck.isCorrupt(loaded)) {
            rejectSavedFile(file);
            return false;
        }
        SpiralSaveCheck.fillMissingCorner(loaded);
        pd = loaded;
        goingToStart = true;
        debugInfo("Spiral loaded: origin %s, size %dx%d", searchArea.coords(pd.spiralOrigin.getX(), pd.spiralOrigin.getZ()),
            pd.spiralWidth, pd.spiralHeight);
        return true;
    }

    private PathingDataSpiral parse(File file) {
        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, PathingDataSpiral.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void rejectSavedFile(File file) {
        ChatUtils.error("The spiral save file %s is unreadable or looks corrupted", file.getAbsolutePath());
        ChatUtils.error("Nothing was overwritten, fix or delete the file, then enable the module again");
        pd = null;
        disable();
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        saveToJson(goingToStart, pd);
        clearTargets();
    }

    @Override
    public void resetState() {
        pd = null;
        goingToStart = true;
        clearTargets();
        teleportGuard.reset();
        debugInfo("Spiral state reset, the next activation starts fresh");
    }

    @Override
    public void onTick() {
        super.onTick();
        if (mc.player == null || mc.world == null || pd == null) return;
        if (isHeldByTeleportGuard()) return;
        if (autosave.isDue()) {
            saveToJson(goingToStart, pd);
        }
        if (System.nanoTime() < paused) {
            releaseForward();
        } else if (isInNether) {
            tickNether();
        } else {
            tickOverworld();
        }
    }

    private boolean isHeldByTeleportGuard() {
        BlockPos here = mc.player.getBlockPos();
        if (!goingToStart && !isRecovering() && teleportGuard.hasTeleported(here)) {
            pauseAfterTeleport();
            return true;
        }
        teleportGuard.remember(here);
        if (!teleportGuard.isPaused()) return false;
        releaseForward();
        return true;
    }

    private void pauseAfterTeleport() {
        pd.currPos = teleportGuard.lastTickPosition();
        saveToJson(true, pd);
        teleportGuard.pause();
        releaseForward();
        stopForTurn();
        ChatUtils.warning("Teleport detected, the spiral is paused");
        ChatUtils.info("Disable and re-enable the module to resume");
    }

    private void tickOverworld() {
        if (goingToStart) {
            returnToSavedPositionOnFoot();
        } else if (isRecovering()) {
            followRecoveryTarget();
        } else {
            flyCurrentLeg();
        }
    }

    private void returnToSavedPositionOnFoot() {
        if (!flyTowards(pd.currPos, SAVED_POSITION_ARRIVAL)) return;
        goingToStart = false;
        nextCorner = null;
        stopForTurn();
    }

    private void followRecoveryTarget() {
        if (!flyTowards(recoveryTarget, RECOVERY_ARRIVAL)) return;
        clearTargets();
        stopForTurn();
    }

    private void flyCurrentLeg() {
        if (nextCorner == null) {
            nextCorner = cornerAtPlayerLevel();
        }
        updateGoalWaypoint(nextCorner);
        BlockPos expected = expectedPosition();
        if (offTrackDistance(expected) > searchArea.spiralMaxOffTrackDistance.get()) {
            beginRecovery(expected);
        } else if (hasReachedCorner()) {
            completeCorner();
            stopForTurn();
        } else {
            holdForward();
            steerYawTowards(Vec3d.ofCenter(nextCorner));
        }
    }

    private BlockPos cornerAtPlayerLevel() {
        BlockPos corner = pd.leg().end(blockGap());
        return new BlockPos(corner.getX(), mc.player.getBlockY(), corner.getZ());
    }

    private BlockPos expectedPosition() {
        BlockPos start = pd.initialPos;
        int y = mc.player.getBlockY();
        if (pd.mainPath) return new BlockPos(clampBetween(mc.player.getX(), start.getX(), nextCorner.getX()), y, start.getZ());
        return new BlockPos(start.getX(), y, clampBetween(mc.player.getZ(), start.getZ(), nextCorner.getZ()));
    }

    private static int clampBetween(double value, int first, int second) {
        return (int) MathHelper.clamp(value, Math.min(first, second), Math.max(first, second));
    }

    private double offTrackDistance(BlockPos expected) {
        if (pd.mainPath) return Math.abs(mc.player.getZ() - expected.getZ());
        return Math.abs(mc.player.getX() - expected.getX());
    }

    private void beginRecovery(BlockPos expected) {
        pd.currPos = expected;
        saveToJson(true, pd);
        ChatUtils.warning("Off the spiral path, flying back to %s", searchArea.coords(expected.getX(), expected.getZ()));
        recoveryTarget = expected;
        nextCorner = null;
        stopForTurn();
    }

    private boolean hasReachedCorner() {
        double distance = AreaMath.horizontalDistance(nextCorner, mc.player.getX(), mc.player.getZ());
        return distance < searchArea.spiralCornerReachDistance.get() || hasPassedCorner();
    }

    private boolean hasPassedCorner() {
        Heading heading = Heading.of(pd.yawDirection);
        double dx = mc.player.getX() - nextCorner.getX();
        double dz = mc.player.getZ() - nextCorner.getZ();
        return dx * heading.stepX() + dz * heading.stepZ() >= 0.0;
    }

    private void completeCorner() {
        BlockPos corner = nextCorner;
        pd.apply(pd.leg().next(blockGap()).startingAt(corner));
        pd.currPos = corner;
        nextCorner = null;
        clearGoalWaypoint();
        saveToJson(true, pd);
        debugInfo("Spiral corner reached at %s, size now %dx%d", searchArea.coords(corner.getX(), corner.getZ()),
            pd.spiralWidth, pd.spiralHeight);
    }

    private void tickNether() {
        if (searchArea.netherPathMode.get() != AreaLoader.NetherPathMode.BARITONE_ELYTRA) return;
        int reach = searchArea.netherWaypointReachDistance.get();
        if (goingToStart) {
            returnToSavedPositionByElytra(reach);
        } else if (nextCorner == null) {
            nextCorner = cornerAtPlayerLevel();
            setBaritoneGoal(nextCorner);
        } else if (AreaMath.horizontalDistance(nextCorner, mc.player.getX(), mc.player.getZ()) < reach) {
            completeCorner();
        } else if (needsNewGoal()) {
            setBaritoneGoal(nextCorner);
        }
    }

    private void returnToSavedPositionByElytra(int reach) {
        if (AreaMath.horizontalDistance(pd.currPos, mc.player.getX(), mc.player.getZ()) < reach) {
            goingToStart = false;
            nextCorner = null;
        } else if (needsNewGoal()) {
            setBaritoneGoal(pd.currPos);
        }
    }

    @Override
    public CoveragePreview coveragePreview() {
        if (!hasUsablePath()) return null;
        double x = mc.player != null ? mc.player.getX() : pd.currPos.getX();
        double z = mc.player != null ? mc.player.getZ() : pd.currPos.getZ();
        return new SpiralPreview(pd.leg(), nextCorner, blockGap()).build(x, z, isRecovering());
    }

    public Snapshot snapshot() {
        if (!hasUsablePath()) return null;
        return new Snapshot(pd.spiralOrigin, pd.initialPos, pd.currPos, pd.spiralWidth, pd.spiralHeight, pd.yawDirection,
            pd.mainPath, goingToStart, isRecovering(), blockGap());
    }

    public boolean validateManualCoordinates(int originX, int originZ, int targetX, int targetZ, int blockGap, boolean apply) {
        BlockPos origin = pathPoint(originX, originZ);
        BlockPos target = pathPoint(targetX, targetZ);
        return apply ? saveResumeAt(origin, target, blockGap) : isOnPath(origin, target, blockGap);
    }

    public int[] snapToNearestCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        return nearestPathPoint(pathPoint(originX, originZ), pathPoint(targetX, targetZ), blockGap);
    }

    public int[] snapToNextCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        return nextCornerPoint(pathPoint(originX, originZ), pathPoint(targetX, targetZ), blockGap);
    }

    public boolean applyFromNextCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        return saveFromNextCorner(pathPoint(originX, originZ), pathPoint(targetX, targetZ), blockGap);
    }

    private boolean isOnPath(BlockPos origin, BlockPos target, int blockGap) {
        return recovery().isNearPath(origin, target, blockGap);
    }

    private boolean saveResumeAt(BlockPos origin, BlockPos target, int blockGap) {
        return recovery().saveResumePoint(origin, target, blockGap);
    }

    private int[] nearestPathPoint(BlockPos origin, BlockPos target, int blockGap) {
        return recovery().nearestPathPoint(origin, target, blockGap);
    }

    private int[] nextCornerPoint(BlockPos origin, BlockPos target, int blockGap) {
        return recovery().nextCorner(origin, target, blockGap);
    }

    private boolean saveFromNextCorner(BlockPos origin, BlockPos target, int blockGap) {
        return recovery().saveFromNextCorner(origin, target, blockGap);
    }

    private SpiralRecovery recovery() {
        return new SpiralRecovery(searchArea, getJsonFile(toString()), this::debugInfo);
    }

    private static BlockPos pathPoint(int x, int z) {
        return new BlockPos(x, SpiralLegSearch.PATH_Y, z);
    }

    public record Snapshot(BlockPos origin, BlockPos legStart, BlockPos current, int width, int height, float yaw,
                           boolean mainPath, boolean goingToStart, boolean recovering, int blockGap) {
    }

    public static class PathingDataSpiral extends PathingData {
        public BlockPos spiralOrigin;
        public int spiralWidth;
        public int spiralHeight;

        public PathingDataSpiral(BlockPos initialPos, BlockPos currPos, float yawDirection, boolean mainPath,
                                 int spiralWidth, int spiralHeight) {
            this.initialPos = initialPos;
            this.currPos = currPos;
            this.yawDirection = yawDirection;
            this.mainPath = mainPath;
            this.spiralWidth = spiralWidth;
            this.spiralHeight = spiralHeight;
            this.spiralOrigin = initialPos;
        }

        SpiralLeg leg() {
            return new SpiralLeg(initialPos, spiralWidth, spiralHeight, yawDirection, mainPath);
        }

        void apply(SpiralLeg leg) {
            initialPos = leg.start();
            spiralWidth = leg.width();
            spiralHeight = leg.height();
            yawDirection = leg.yaw();
            mainPath = leg.mainPath();
        }
    }

    public static class SpiralLegInfo {
        public final int startX;
        public final int startZ;
        public final int endX;
        public final int endZ;
        public final int spiralWidth;
        public final int spiralHeight;
        public final float yaw;
        public final boolean mainPath;
        public final int legNumber;

        SpiralLegInfo(int legNumber, SpiralLeg leg, int blockGap) {
            BlockPos end = leg.end(blockGap);
            this.startX = leg.start().getX();
            this.startZ = leg.start().getZ();
            this.endX = end.getX();
            this.endZ = end.getZ();
            this.spiralWidth = leg.width();
            this.spiralHeight = leg.height();
            this.yaw = leg.yaw();
            this.mainPath = leg.mainPath();
            this.legNumber = legNumber;
        }

        Segment segment() {
            return new Segment(startX, startZ, endX, endZ);
        }

        SpiralLeg leg() {
            BlockPos start = new BlockPos(startX, SpiralLegSearch.PATH_Y, startZ);
            return new SpiralLeg(start, spiralWidth, spiralHeight, yaw, mainPath);
        }
    }
}
