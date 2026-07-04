package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.regear.arealoader.AreaLoaderMode;
import com.hunterbuddy.modules.regear.arealoader.AreaLoaderModes;
import com.hunterbuddy.modules.regear.util.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/** Reference parity: mlep.modules.arealoader.modes.Spiral. 100% faithful port. */
public class Spiral extends AreaLoaderMode {
    private PathingDataSpiral pd;
    private boolean goingToStart = true;
    private long startTime;
    private BlockPos nextCornerTarget = null;
    private boolean recovering = false;
    private BlockPos recoveryTarget = null;
    private BlockPos lastKnownGoodPosition = null;
    private BlockPos lastTickPos = null;
    private static final int TELEPORT_THRESHOLD = 100;
    private boolean teleportPaused = false;

    public Spiral() {
        super(AreaLoaderModes.Spiral);
    }

    @Override
    public void onActivate() {
        this.startTime = System.nanoTime();
        this.goingToStart = true;
        this.nextCornerTarget = null;
        this.recovering = false;
        this.recoveryTarget = null;
        this.lastTickPos = null;
        this.teleportPaused = false;
        this.lastKnownGoodPosition = null;
        File file = this.getJsonFile(super.toString());
        if (file == null) {
            this.debugInfo("Error: Cannot create save file path. Check save-name setting.");
            this.pd = new PathingDataSpiral(this.mc.player.getBlockPos(), this.mc.player.getBlockPos(), -90.0F, true, 0, 0);
            this.goingToStart = false;
        } else if (!file.exists()) {
            this.pd = new PathingDataSpiral(this.mc.player.getBlockPos(), this.mc.player.getBlockPos(), -90.0F, true, 0, 0);
            this.goingToStart = false;
            this.debugInfo("Spiral started from origin: " + this.pd.spiralOrigin.toShortString() + " (no save file found at: " + file.getAbsolutePath() + ")");
        } else {
            try {
                this.debugInfo("Loading save file: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");
                FileReader reader = new FileReader(file);
                this.pd = (PathingDataSpiral) GSON.fromJson(reader, PathingDataSpiral.class);
                reader.close();
                boolean corrupted = false;
                String corruptionReason = "";
                if (this.pd == null) { corrupted = true; corruptionReason = "pd is null"; }
                else if (this.pd.spiralOrigin == null && this.pd.initialPos == null) { corrupted = true; corruptionReason = "both spiralOrigin and initialPos are null"; }
                else if (this.pd.currPos == null) { corrupted = true; corruptionReason = "currPos is null"; }
                else if (this.pd.spiralOrigin != null && this.pd.spiralWidth == 0 && this.pd.spiralHeight == 0) {
                    BlockPos origin = this.pd.spiralOrigin;
                    BlockPos curr = this.pd.currPos;
                    double distFromOrigin = Math.sqrt(curr.getSquaredDistance(origin));
                    if (distFromOrigin > 1000.0) { corrupted = true; corruptionReason = String.format("spiralWidth/Height are 0 but currPos is %.0f blocks from origin - save may have been reset", distFromOrigin); }
                }
                if (corrupted) {
                    ChatUtils.error("SAVE FILE APPEARS CORRUPTED: " + corruptionReason);
                    ChatUtils.error("NOT starting fresh to protect your progress. Please fix the save file manually.");
                    ChatUtils.error("Save file location: " + file.getAbsolutePath());
                    ChatUtils.error("Disabling module to prevent data loss.");
                    this.pd = null;
                    this.disable();
                    return;
                }
                if (this.pd.spiralOrigin == null) {
                    this.pd.spiralOrigin = this.pd.initialPos;
                    this.debugInfo("Loaded legacy save - set origin to: " + this.pd.spiralOrigin.toShortString());
                }
                this.debugInfo("Loaded saved path successfully. Origin: " + this.pd.spiralOrigin.toShortString() + ", Current: " + this.pd.currPos.toShortString());
                this.debugInfo("Spiral state: width=%d, height=%d, yaw=%.1f, mainPath=%b", this.pd.spiralWidth, this.pd.spiralHeight, this.pd.yawDirection, this.pd.mainPath);
            } catch (Exception e) {
                ChatUtils.error("Failed to load saved path: " + e.getMessage());
                ChatUtils.error("NOT starting fresh to protect your progress. Please check the save file.");
                ChatUtils.error("Save file location: " + file.getAbsolutePath());
                this.pd = null;
                this.disable();
                return;
            }
        }
        this.initializeFlightModes();
    }

    @Override
    public void onDeactivate() {
        super.onDeactivate();
        super.saveToJson(this.goingToStart, this.pd);
        this.nextCornerTarget = null;
        this.recoveryTarget = null;
        this.recovering = false;
    }

    @Override
    public void resetState() {
        this.pd = null;
        this.goingToStart = true;
        this.nextCornerTarget = null;
        this.recovering = false;
        this.recoveryTarget = null;
        this.lastTickPos = null;
        this.teleportPaused = false;
        this.lastKnownGoodPosition = null;
        this.debugInfo("Spiral state reset. Next activation will start fresh.");
    }

    @Override
    public void onTick() {
        super.onTick();
        if (this.mc.player != null && this.mc.world != null && this.pd != null) {
            BlockPos currentPos = this.mc.player.getBlockPos();
            if (this.lastTickPos != null && !this.goingToStart && !this.recovering && !this.teleportPaused) {
                double tickDistance = Math.sqrt(this.lastTickPos.getSquaredDistance(currentPos));
                if (tickDistance > 100.0) {
                    this.debugInfo("TELEPORT DETECTED! Moved %.0f blocks in one tick. Pausing spiral to protect state.", tickDistance);
                    this.debugInfo("Last safe position: X=%d Z=%d. Current: X=%d Z=%d", this.lastTickPos.getX(), this.lastTickPos.getZ(), currentPos.getX(), currentPos.getZ());
                    this.pd.currPos = this.lastTickPos;
                    super.saveToJson(false, this.pd);
                    this.teleportPaused = true;
                    Utils.setPressed(this.mc.options.forwardKey, false);
                    this.mc.player.setVelocity(0.0, 0.0, 0.0);
                    ChatUtils.info("Spiral PAUSED due to teleportation. State saved at last safe position.");
                    ChatUtils.info("Disable and re-enable the module to resume from saved position.");
                    return;
                }
            }
            this.lastTickPos = currentPos;
            if (this.teleportPaused) Utils.setPressed(this.mc.options.forwardKey, false);
            else {
                if (System.nanoTime() - this.startTime > 6.0E11) {
                    this.startTime = System.nanoTime();
                    super.saveToJson(this.goingToStart, this.pd);
                }
                if (System.nanoTime() < this.paused) Utils.setPressed(this.mc.options.forwardKey, false);
                else {
                    if (this.isInNether) this.onTickNether();
                    else this.onTickOverworld();
                }
            }
        }
    }

    private void onTickOverworld() {
        if (this.goingToStart) {
            this.updateGoalWaypoint(this.pd.currPos);
            if (Math.sqrt(this.mc.player.getBlockPos().getSquaredDistance(this.pd.currPos.getX(), this.mc.player.getY(), this.pd.currPos.getZ())) < 5.0) {
                this.goingToStart = false;
                this.nextCornerTarget = null;
                this.mc.player.setVelocity(0.0, 0.0, 0.0);
            } else {
                this.steerYawTowards(this.pd.currPos.toCenterPos());
                Utils.setPressed(this.mc.options.forwardKey, true);
            }
        } else if (this.recovering) {
            if (this.recoveryTarget == null) this.recovering = false;
            else if (Math.sqrt(this.mc.player.getBlockPos().getSquaredDistance(this.recoveryTarget.getX(), this.mc.player.getY(), this.recoveryTarget.getZ())) < 20.0) {
                this.debugInfo("Recovery complete. Resuming spiral at X=%d Z=%d", this.recoveryTarget.getX(), this.recoveryTarget.getZ());
                this.recovering = false;
                this.recoveryTarget = null;
                this.nextCornerTarget = null;
                this.mc.player.setVelocity(0.0, 0.0, 0.0);
            } else {
                this.updateGoalWaypoint(this.recoveryTarget);
                this.steerYawTowards(this.recoveryTarget.toCenterPos());
                Utils.setPressed(this.mc.options.forwardKey, true);
            }
        } else {
            int blockGap = 16 * this.searchArea.rowGap.get();
            if (this.nextCornerTarget == null) this.nextCornerTarget = this.calculateExactCornerTarget(blockGap);
            this.updateGoalWaypoint(this.nextCornerTarget);
            BlockPos expectedPos = this.calculateExpectedPosition(blockGap);
            double offTrackDistance = this.getOffTrackDistance(expectedPos);
            if (offTrackDistance > this.searchArea.spiralMaxOffTrackDistance.get()) {
                this.debugInfo("Off-track by %.0f blocks! Expected near X=%d Z=%d. Saving state and initiating recovery...", offTrackDistance, expectedPos.getX(), expectedPos.getZ());
                this.pd.currPos = expectedPos;
                super.saveToJson(false, this.pd);
                ChatUtils.info("Saved recovery point at X=%d Z=%d. Navigating back to resume spiral.", expectedPos.getX(), expectedPos.getZ());
                this.recovering = true;
                this.recoveryTarget = expectedPos;
                this.nextCornerTarget = null;
                this.mc.player.setVelocity(0.0, 0.0, 0.0);
            } else {
                if (this.hasReachedCorner(this.nextCornerTarget)) {
                    BlockPos snappedCorner = this.nextCornerTarget;
                    this.pd.yawDirection = this.normalizeYaw(this.pd.yawDirection + 90.0F);
                    this.pd.initialPos = new BlockPos(snappedCorner.getX(), this.pd.initialPos.getY(), snappedCorner.getZ());
                    if (this.pd.mainPath) { this.pd.spiralWidth += blockGap; this.pd.mainPath = false; }
                    else { this.pd.spiralHeight += blockGap; this.pd.mainPath = true; }
                    this.mc.player.setVelocity(0.0, 0.0, 0.0);
                    this.pd.currPos = snappedCorner;
                    this.updateGoalWaypoint(null);
                    this.nextCornerTarget = null;
                    super.saveToJson(false, this.pd);
                    this.debugInfo("Spiral: Turned corner at X=%d Z=%d (yaw=%.1f, mainPath=%b, width=%d, height=%d)", snappedCorner.getX(), snappedCorner.getZ(), this.pd.yawDirection, this.pd.mainPath, this.pd.spiralWidth, this.pd.spiralHeight);
                } else {
                    Utils.setPressed(this.mc.options.forwardKey, true);
                    this.steerYawTowards(this.nextCornerTarget.toCenterPos());
                }
            }
        }
    }

    private BlockPos calculateExactCornerTarget(int blockGap) {
        float normalizedYaw = this.normalizeYaw(this.pd.yawDirection);
        int targetX;
        int targetZ;
        if (this.pd.mainPath) {
            targetZ = this.pd.initialPos.getZ();
            if (normalizedYaw >= 225.0F && normalizedYaw < 315.0F) targetX = this.pd.initialPos.getX() + blockGap + this.pd.spiralWidth;
            else targetX = this.pd.initialPos.getX() - (blockGap + this.pd.spiralWidth);
        } else {
            targetX = this.pd.initialPos.getX();
            if (!(normalizedYaw >= 315.0F) && !(normalizedYaw < 45.0F)) targetZ = this.pd.initialPos.getZ() - (blockGap + this.pd.spiralHeight);
            else targetZ = this.pd.initialPos.getZ() + blockGap + this.pd.spiralHeight;
        }
        return new BlockPos(targetX, this.mc.player.getBlockY(), targetZ);
    }

    private boolean hasReachedCorner(BlockPos corner) {
        double distToCorner = Math.sqrt(this.mc.player.getBlockPos().getSquaredDistance(corner.getX(), this.mc.player.getY(), corner.getZ()));
        if (distToCorner < this.searchArea.spiralCornerReachDistance.get()) return true;
        if (this.pd.mainPath) {
            boolean goingPositive = corner.getX() >= this.pd.initialPos.getX();
            return goingPositive ? this.mc.player.getX() >= corner.getX() : this.mc.player.getX() <= corner.getX();
        } else {
            boolean goingPositive = corner.getZ() >= this.pd.initialPos.getZ();
            return goingPositive ? this.mc.player.getZ() >= corner.getZ() : this.mc.player.getZ() <= corner.getZ();
        }
    }

    private BlockPos calculateExpectedPosition(int blockGap) {
        float normalizedYaw = this.normalizeYaw(this.pd.yawDirection);
        int playerX = this.mc.player.getBlockX();
        int playerZ = this.mc.player.getBlockZ();
        if (this.pd.mainPath) {
            int expectedZ = this.pd.initialPos.getZ();
            int targetX = (normalizedYaw >= 225.0F && normalizedYaw < 315.0F)
                ? this.pd.initialPos.getX() + blockGap + this.pd.spiralWidth
                : this.pd.initialPos.getX() - (blockGap + this.pd.spiralWidth);
            int expectedX = this.clamp(playerX, Math.min(this.pd.initialPos.getX(), targetX), Math.max(this.pd.initialPos.getX(), targetX));
            return new BlockPos(expectedX, this.mc.player.getBlockY(), expectedZ);
        } else {
            int expectedX = this.pd.initialPos.getX();
            int targetZ = (!(normalizedYaw >= 315.0F) && !(normalizedYaw < 45.0F))
                ? this.pd.initialPos.getZ() - (blockGap + this.pd.spiralHeight)
                : this.pd.initialPos.getZ() + blockGap + this.pd.spiralHeight;
            int expectedZ = this.clamp(playerZ, Math.min(this.pd.initialPos.getZ(), targetZ), Math.max(this.pd.initialPos.getZ(), targetZ));
            return new BlockPos(expectedX, this.mc.player.getBlockY(), expectedZ);
        }
    }

    private double getOffTrackDistance(BlockPos expectedPos) {
        return this.pd.mainPath
            ? Math.abs(this.mc.player.getZ() - expectedPos.getZ())
            : Math.abs(this.mc.player.getX() - expectedPos.getX());
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double distanceXZ(int x1, int z1, int x2, int z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void onTickNether() {
        if (this.searchArea.netherPathMode.get() != AreaLoader.NetherPathMode.BARITONE_ELYTRA) this.steerYaw(this.pd.yawDirection);
        else {
            int blockGap = 16 * this.searchArea.rowGap.get();
            int reachDist = this.searchArea.netherWaypointReachDistance.get();
            if (this.goingToStart) {
                double distToSaved = Math.sqrt(this.mc.player.getBlockPos().getSquaredDistance(this.pd.currPos.getX(), this.mc.player.getY(), this.pd.currPos.getZ()));
                if (distToSaved < reachDist) {
                    this.goingToStart = false;
                    this.nextCornerTarget = null;
                    this.debugInfo("Reached saved position. Resuming spiral at yaw=%.1f, mainPath=%b, width=%d, height=%d", this.pd.yawDirection, this.pd.mainPath, this.pd.spiralWidth, this.pd.spiralHeight);
                } else if (this.needsNewGoal()) this.setBaritoneGoal(this.pd.currPos);
            } else if (this.nextCornerTarget == null) {
                this.calculateNextCorner(blockGap);
                if (this.nextCornerTarget != null) this.setBaritoneGoal(this.nextCornerTarget);
            } else {
                double distToCorner = Math.sqrt(this.mc.player.getBlockPos().getSquaredDistance(this.nextCornerTarget.getX(), this.mc.player.getY(), this.nextCornerTarget.getZ()));
                if (distToCorner < reachDist) {
                    BlockPos snappedCorner = this.nextCornerTarget;
                    this.debugInfo("Spiral: Reached corner at X=%d Z=%d, yaw was %.1f", snappedCorner.getX(), snappedCorner.getZ(), this.pd.yawDirection);
                    this.pd.yawDirection = this.normalizeYaw(this.pd.yawDirection + 90.0F);
                    this.pd.initialPos = new BlockPos(snappedCorner.getX(), this.pd.initialPos.getY(), snappedCorner.getZ());
                    if (this.pd.mainPath) { this.pd.spiralWidth += blockGap; this.pd.mainPath = false; }
                    else { this.pd.spiralHeight += blockGap; this.pd.mainPath = true; }
                    this.pd.currPos = snappedCorner;
                    this.lastKnownGoodPosition = snappedCorner;
                    this.updateGoalWaypoint(null);
                    this.nextCornerTarget = null;
                    super.saveToJson(false, this.pd);
                    this.debugInfo("Spiral: Turned to yaw=%.1f, mainPath=%b, width=%d, height=%d (saved)", this.pd.yawDirection, this.pd.mainPath, this.pd.spiralWidth, this.pd.spiralHeight);
                } else if (this.needsNewGoal()) {
                    this.debugInfo("Spiral: Baritone needs new goal, resetting to corner target");
                    this.setBaritoneGoal(this.nextCornerTarget);
                }
            }
        }
    }

    private float normalizeYaw(float yaw) {
        yaw %= 360.0F;
        if (yaw < 0.0F) yaw += 360.0F;
        return yaw;
    }

    private void calculateNextCorner(int blockGap) {
        float normalizedYaw = this.normalizeYaw(this.pd.yawDirection);
        int targetX;
        int targetZ;
        if (this.pd.mainPath) {
            int xDirection = (normalizedYaw >= 225.0F && normalizedYaw < 315.0F) ? 1 : -1;
            targetX = this.pd.initialPos.getX() + xDirection * (blockGap + this.pd.spiralWidth);
            targetZ = this.pd.initialPos.getZ();
        } else {
            int zDirection = (normalizedYaw >= 315.0F || normalizedYaw < 45.0F) ? 1 : -1;
            targetX = this.pd.initialPos.getX();
            targetZ = this.pd.initialPos.getZ() + zDirection * (blockGap + this.pd.spiralHeight);
        }
        this.nextCornerTarget = new BlockPos(targetX, this.mc.player.getBlockY(), targetZ);
        this.updateGoalWaypoint(this.nextCornerTarget);
        this.debugInfo("Spiral: Next corner target at X=%d, Z=%d (yaw=%.1f, mainPath=%b)", targetX, targetZ, normalizedYaw, this.pd.mainPath);
    }

    private List<SpiralLegInfo> generateSpiralLegs(int originX, int originZ, int blockGap, int maxLegs) {
        List<SpiralLegInfo> legs = new ArrayList<>();
        int x = originX;
        int z = originZ;
        float yaw = 270.0F;
        boolean mainPath = true;
        int spiralWidth = 0;
        int spiralHeight = 0;
        for (int legNum = 0; legNum < maxLegs; legNum++) {
            int startX = x;
            int startZ = z;
            float normYaw = this.normalizeYaw(yaw);
            int endX;
            int endZ;
            if (mainPath) {
                endZ = z;
                if (normYaw >= 225.0F && normYaw < 315.0F) endX = x + blockGap + spiralWidth;
                else endX = x - (blockGap + spiralWidth);
            } else {
                endX = x;
                if (!(normYaw >= 315.0F) && !(normYaw < 45.0F)) endZ = z - (blockGap + spiralHeight);
                else endZ = z + blockGap + spiralHeight;
            }
            legs.add(new SpiralLegInfo(startX, startZ, endX, endZ, spiralWidth, spiralHeight, yaw, mainPath, legNum));
            x = endX;
            z = endZ;
            yaw = this.normalizeYaw(yaw + 90.0F);
            if (mainPath) { spiralWidth += blockGap; mainPath = false; }
            else { spiralHeight += blockGap; mainPath = true; }
        }
        return legs;
    }

    private SpiralLegInfo findClosestLeg(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        int dxFromOrigin = Math.abs(targetX - originX);
        int dzFromOrigin = Math.abs(targetZ - originZ);
        int maxCoordDist = Math.max(dxFromOrigin, dzFromOrigin);
        int estimatedLegs = (maxCoordDist / blockGap + 1) * 4 + 500;
        estimatedLegs = Math.max(500, Math.min(estimatedLegs, 500000));
        this.debugInfo("Search params: origin=(%d,%d) target=(%d,%d) gap=%d", originX, originZ, targetX, targetZ, blockGap);
        this.debugInfo("Generating %d spiral legs (max coord dist = %d)", estimatedLegs, maxCoordDist);
        List<SpiralLegInfo> legs = this.generateSpiralLegs(originX, originZ, blockGap, estimatedLegs);
        this.debugInfo("First 4 legs of generated spiral:");
        for (int i = 0; i < Math.min(4, legs.size()); i++) {
            SpiralLegInfo l = legs.get(i);
            this.debugInfo("  Leg %d: (%d,%d)->(%d,%d) yaw=%.0f mp=%b w=%d h=%d", i, l.startX, l.startZ, l.endX, l.endZ, l.yaw, l.mainPath, l.spiralWidth, l.spiralHeight);
        }
        List<SpiralLegInfo> nearbyLegs = new ArrayList<>();
        for (SpiralLegInfo leg : legs) {
            if (leg.distanceToLeg(targetX, targetZ) < blockGap * 3) nearbyLegs.add(leg);
        }
        if (!nearbyLegs.isEmpty()) {
            this.debugInfo("Legs near target position (%d found):", nearbyLegs.size());
            for (int i = 0; i < Math.min(5, nearbyLegs.size()); i++) {
                SpiralLegInfo l = nearbyLegs.get(i);
                this.debugInfo("  Leg %d: (%d,%d)->(%d,%d) dist=%.0f yaw=%.0f mp=%b", l.legNumber, l.startX, l.startZ, l.endX, l.endZ, l.distanceToLeg(targetX, targetZ), l.yaw, l.mainPath);
            }
        }
        double tolerance = blockGap * 1.5;
        List<SpiralLegInfo> candidates = new ArrayList<>();
        for (SpiralLegInfo leg : legs) {
            double dist = leg.distanceToLeg(targetX, targetZ);
            if (dist <= tolerance) candidates.add(leg);
        }
        this.debugInfo("Found %d candidate legs within tolerance %.0f blocks", candidates.size(), tolerance);
        if (candidates.isEmpty()) {
            SpiralLegInfo closest = null;
            double closestDist = Double.MAX_VALUE;
            for (SpiralLegInfo leg : legs) {
                double dist = leg.distanceToLeg(targetX, targetZ);
                if (dist < closestDist) { closestDist = dist; closest = leg; }
            }
            this.debugInfo("No candidates within tolerance, using closest leg #%d at distance %.0f", closest != null ? closest.legNumber : -1, closestDist);
            return closest;
        } else {
            List<SpiralLegInfo> notPastEnd = new ArrayList<>();
            for (SpiralLegInfo leg : candidates) {
                int[] proj = leg.projectOntoLeg(targetX, targetZ);
                double legLength = distanceXZ(leg.startX, leg.startZ, leg.endX, leg.endZ);
                double projDist = distanceXZ(leg.startX, leg.startZ, proj[0], proj[1]);
                if (projDist < legLength * 0.95) notPastEnd.add(leg);
            }
            List<SpiralLegInfo> pool = notPastEnd.isEmpty() ? candidates : notPastEnd;
            SpiralLegInfo best = null;
            for (SpiralLegInfo leg : pool) {
                if (best == null || leg.legNumber > best.legNumber) best = leg;
            }
            if (best != null) {
                double distToBest = best.distanceToLeg(targetX, targetZ);
                double closestPossible = Double.MAX_VALUE;
                for (SpiralLegInfo leg : legs) {
                    double dist = leg.distanceToLeg(targetX, targetZ);
                    if (dist < closestPossible) closestPossible = dist;
                }
                if (distToBest > closestPossible * 2.0 + 50.0) {
                    for (SpiralLegInfo leg : legs) {
                        if (leg.distanceToLeg(targetX, targetZ) <= closestPossible + 10.0) {
                            this.debugInfo("Selected Leg #%d (closest): (%d,%d)->(%d,%d) dist=%.0f", leg.legNumber, leg.startX, leg.startZ, leg.endX, leg.endZ, leg.distanceToLeg(targetX, targetZ));
                            return leg;
                        }
                    }
                }
            }
            if (best != null) {
                this.debugInfo("Selected Leg #%d (best candidate): (%d,%d)->(%d,%d) dist=%.0f", best.legNumber, best.startX, best.startZ, best.endX, best.endZ, best.distanceToLeg(targetX, targetZ));
            }
            return best;
        }
    }

    private List<SpiralCornerState> generateSpiralCorners(int originX, int originZ, int blockGap, int maxCorners) {
        List<SpiralCornerState> corners = new ArrayList<>();
        int x = originX;
        int z = originZ;
        float yaw = 270.0F;
        boolean mainPath = true;
        int spiralWidth = 0;
        int spiralHeight = 0;
        corners.add(new SpiralCornerState(x, z, spiralWidth, spiralHeight, yaw, mainPath, 0));
        for (int i = 1; i <= maxCorners; i++) {
            float normYaw = this.normalizeYaw(yaw);
            if (mainPath) {
                if (normYaw >= 225.0F && normYaw < 315.0F) x += blockGap + spiralWidth;
                else x -= blockGap + spiralWidth;
            } else if (!(normYaw >= 315.0F) && !(normYaw < 45.0F)) z -= blockGap + spiralHeight;
            else z += blockGap + spiralHeight;
            yaw = this.normalizeYaw(yaw + 90.0F);
            if (mainPath) { spiralWidth += blockGap; mainPath = false; }
            else { spiralHeight += blockGap; mainPath = true; }
            corners.add(new SpiralCornerState(x, z, spiralWidth, spiralHeight, yaw, mainPath, i));
        }
        return corners;
    }

    private SpiralCornerState findClosestCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        double distFromOrigin = distanceXZ(originX, originZ, targetX, targetZ);
        int estimatedCorners = (int) (distFromOrigin / blockGap * 4.0) + 20;
        estimatedCorners = Math.max(100, Math.min(estimatedCorners, 10000));
        List<SpiralCornerState> corners = this.generateSpiralCorners(originX, originZ, blockGap, estimatedCorners);
        SpiralCornerState closest = null;
        double closestDist = Double.MAX_VALUE;
        for (SpiralCornerState corner : corners) {
            double dist = distanceXZ(corner.cornerX, corner.cornerZ, targetX, targetZ);
            if (dist < closestDist) { closestDist = dist; closest = corner; }
            if (dist == 0.0) break;
        }
        return closest;
    }

    public boolean validateManualCoordinates(int originX, int originZ, int targetX, int targetZ, int blockGap, boolean apply) {
        this.debugInfo("Finding spiral leg for position (%d, %d) with origin (%d, %d), gap=%d blocks", targetX, targetZ, originX, originZ, blockGap);
        SpiralLegInfo closestLeg = this.findClosestLeg(originX, originZ, targetX, targetZ, blockGap);
        if (closestLeg == null) { ChatUtils.error("Could not calculate spiral path. Check your settings."); return false; }
        double distanceToLeg = closestLeg.distanceToLeg(targetX, targetZ);
        int[] projected = closestLeg.projectOntoLeg(targetX, targetZ);
        int expectedEndX;
        int expectedEndZ;
        if (closestLeg.mainPath) {
            expectedEndZ = closestLeg.startZ;
            float normYaw = this.normalizeYaw(closestLeg.yaw);
            if (normYaw >= 225.0F && normYaw < 315.0F) expectedEndX = closestLeg.startX + blockGap + closestLeg.spiralWidth;
            else expectedEndX = closestLeg.startX - (blockGap + closestLeg.spiralWidth);
        } else {
            expectedEndX = closestLeg.startX;
            float normYaw = this.normalizeYaw(closestLeg.yaw);
            if (!(normYaw >= 315.0F) && !(normYaw < 45.0F)) expectedEndZ = closestLeg.startZ - (blockGap + closestLeg.spiralHeight);
            else expectedEndZ = closestLeg.startZ + blockGap + closestLeg.spiralHeight;
        }
        this.debugInfo("Found Leg #%d:", closestLeg.legNumber);
        this.debugInfo("  Start: (%d, %d)", closestLeg.startX, closestLeg.startZ);
        this.debugInfo("  End:   (%d, %d)", closestLeg.endX, closestLeg.endZ);
        this.debugInfo("  Next corner will be at: (%d, %d)", expectedEndX, expectedEndZ);
        this.debugInfo("  Direction: yaw=%.0f (%s), mainPath=%b", closestLeg.yaw, this.getDirectionName(closestLeg.yaw), closestLeg.mainPath);
        this.debugInfo("  Spiral state: width=%d, height=%d", closestLeg.spiralWidth, closestLeg.spiralHeight);
        this.debugInfo("  Distance from path: %.0f blocks", distanceToLeg);
        if (distanceToLeg > blockGap / 2) this.debugInfo("  Projected position onto leg: (%d, %d)", projected[0], projected[1]);
        if (apply) {
            if (distanceToLeg > blockGap * 2) {
                ChatUtils.error("Position is too far from spiral path (%.0f blocks). Max tolerance: %d blocks.", distanceToLeg, blockGap * 2);
                ChatUtils.error("Make sure you entered the correct origin coordinates where the spiral started.");
                return false;
            }
            int resumeX = distanceToLeg < 50.0 ? targetX : projected[0];
            int resumeZ = distanceToLeg < 50.0 ? targetZ : projected[1];
            PathingDataSpiral newPd = new PathingDataSpiral(new BlockPos(closestLeg.startX, 64, closestLeg.startZ), new BlockPos(resumeX, 64, resumeZ), closestLeg.yaw, closestLeg.mainPath, closestLeg.spiralWidth, closestLeg.spiralHeight);
            newPd.spiralOrigin = new BlockPos(originX, 64, originZ);
            File file = this.getJsonFile(this.toString());
            if (file == null) { ChatUtils.error("Failed to get save file path."); return false; }
            try {
                if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
                Writer writer = new FileWriter(file);
                GSON.toJson(newPd, writer);
                writer.flush();
                writer.close();
                this.debugInfo("SAVED spiral recovery state:");
                this.debugInfo("  Origin: (%d, %d)", originX, originZ);
                this.debugInfo("  Resume from: (%d, %d)", resumeX, resumeZ);
                this.debugInfo("  Initial pos (leg start): (%d, %d)", closestLeg.startX, closestLeg.startZ);
                this.debugInfo("  Next target: (%d, %d) going %s", expectedEndX, expectedEndZ, this.getDirectionName(closestLeg.yaw));
                this.debugInfo("Enable the module to resume the spiral.");
                return true;
            } catch (Exception e) { ChatUtils.error("Failed to save: " + e.getMessage()); return false; }
        } else return distanceToLeg < blockGap / 2;
    }

    private String getDirectionName(float yaw) {
        float norm = this.normalizeYaw(yaw);
        if (norm >= 315.0F || norm < 45.0F) return "South (+Z)";
        else if (norm >= 45.0F && norm < 135.0F) return "West (-X)";
        else return norm >= 135.0F && norm < 225.0F ? "North (-Z)" : "East (+X)";
    }

    public int[] snapToNearestCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        this.debugInfo("Snapping position (%d, %d) to spiral path...", targetX, targetZ);
        SpiralLegInfo closestLeg = this.findClosestLeg(originX, originZ, targetX, targetZ, blockGap);
        if (closestLeg == null) { ChatUtils.error("Could not calculate spiral path."); return null; }
        int[] projected = closestLeg.projectOntoLeg(targetX, targetZ);
        double distance = closestLeg.distanceToLeg(targetX, targetZ);
        this.debugInfo("Snapped to Leg #%d at position (%d, %d)", closestLeg.legNumber, projected[0], projected[1]);
        this.debugInfo("  Was %.0f blocks off the spiral path", distance);
        this.debugInfo("  This leg goes from (%d, %d) to (%d, %d) heading %s", closestLeg.startX, closestLeg.startZ, closestLeg.endX, closestLeg.endZ, this.getDirectionName(closestLeg.yaw));
        return projected;
    }

    public int[] snapToNextCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        this.debugInfo("=== SNAP TO NEXT CORNER ===");
        this.debugInfo("Input: origin=(%d, %d), target=(%d, %d), blockGap=%d", originX, originZ, targetX, targetZ, blockGap);
        SpiralLegInfo closestLeg = this.findClosestLeg(originX, originZ, targetX, targetZ, blockGap);
        if (closestLeg == null) { ChatUtils.error("Could not calculate spiral path."); return null; }
        int nextCornerX = closestLeg.endX;
        int nextCornerZ = closestLeg.endZ;
        this.debugInfo("Found Leg #%d: (%d, %d) -> (%d, %d)", closestLeg.legNumber, closestLeg.startX, closestLeg.startZ, closestLeg.endX, closestLeg.endZ);
        this.debugInfo("Next corner: (%d, %d) going %s", nextCornerX, nextCornerZ, this.getDirectionName(closestLeg.yaw));
        return new int[]{nextCornerX, nextCornerZ};
    }

    public boolean applyFromNextCorner(int originX, int originZ, int targetX, int targetZ, int blockGap) {
        this.debugInfo("=== APPLY FROM NEXT CORNER ===");
        this.debugInfo("Input: origin=(%d, %d), target=(%d, %d), blockGap=%d", originX, originZ, targetX, targetZ, blockGap);
        SpiralLegInfo closestLeg = this.findClosestLeg(originX, originZ, targetX, targetZ, blockGap);
        if (closestLeg == null) { ChatUtils.error("Could not find your position on the spiral."); return false; }
        int cornerX = closestLeg.endX;
        int cornerZ = closestLeg.endZ;
        float newYaw = this.normalizeYaw(closestLeg.yaw + 90.0F);
        boolean newMainPath;
        int newWidth;
        int newHeight;
        if (closestLeg.mainPath) { newWidth = closestLeg.spiralWidth + blockGap; newHeight = closestLeg.spiralHeight; newMainPath = false; }
        else { newWidth = closestLeg.spiralWidth; newHeight = closestLeg.spiralHeight + blockGap; newMainPath = true; }
        float normNewYaw = this.normalizeYaw(newYaw);
        int nextTargetX;
        int nextTargetZ;
        if (newMainPath) {
            nextTargetZ = cornerZ;
            if (normNewYaw >= 225.0F && normNewYaw < 315.0F) nextTargetX = cornerX + blockGap + newWidth;
            else nextTargetX = cornerX - (blockGap + newWidth);
        } else {
            nextTargetX = cornerX;
            if (!(normNewYaw >= 315.0F) && !(normNewYaw < 45.0F)) nextTargetZ = cornerZ - (blockGap + newHeight);
            else nextTargetZ = cornerZ + blockGap + newHeight;
        }
        this.debugInfo("Current position near leg #%d", closestLeg.legNumber);
        this.debugInfo("Will snap to corner: (%d, %d)", cornerX, cornerZ);
        this.debugInfo("After turn, next target: (%d, %d) going %s", nextTargetX, nextTargetZ, this.getDirectionName(newYaw));
        this.debugInfo("New state: yaw=%.0f, mainPath=%b, width=%d, height=%d", newYaw, newMainPath, newWidth, newHeight);
        PathingDataSpiral newPd = new PathingDataSpiral(new BlockPos(cornerX, 64, cornerZ), new BlockPos(cornerX, 64, cornerZ), newYaw, newMainPath, newWidth, newHeight);
        newPd.spiralOrigin = new BlockPos(originX, 64, originZ);
        this.debugInfo("Getting save file for '%s'", this.toString());
        File file = this.getJsonFile(this.toString());
        if (file == null) { ChatUtils.error("Failed to get save file path. Check that you're in a world."); return false; }
        this.debugInfo("Will save to: %s", file.getAbsolutePath());
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            Writer writer = new FileWriter(file);
            GSON.toJson(newPd, writer);
            writer.flush();
            writer.close();
            this.debugInfo("=== SAVED ===");
            this.debugInfo("Go to (%d, %d) and enable the module.", cornerX, cornerZ);
            this.debugInfo("It will head toward (%d, %d)", nextTargetX, nextTargetZ);
            return true;
        } catch (Exception e) { ChatUtils.error("Failed to save: " + e.getMessage()); return false; }
    }

    public static class PathingDataSpiral extends AreaLoaderMode.PathingData {
        public BlockPos spiralOrigin;
        public int spiralWidth = 0;
        public int spiralHeight = 0;

        public PathingDataSpiral(BlockPos initialPos, BlockPos currPos, float yawDirection, boolean mainPath, int spiralWidth, int spiralHeight) {
            this.spiralOrigin = initialPos;
            this.initialPos = initialPos;
            this.currPos = currPos;
            this.yawDirection = yawDirection;
            this.mainPath = mainPath;
            this.spiralWidth = spiralWidth;
            this.spiralHeight = spiralHeight;
        }
    }

    private static class SpiralCornerState {
        int cornerX;
        int cornerZ;
        int spiralWidth;
        int spiralHeight;
        float yaw;
        boolean mainPath;
        int cornerNumber;

        SpiralCornerState(int x, int z, int w, int h, float y, boolean mp, int num) {
            this.cornerX = x;
            this.cornerZ = z;
            this.spiralWidth = w;
            this.spiralHeight = h;
            this.yaw = y;
            this.mainPath = mp;
            this.cornerNumber = num;
        }
    }

    public static class SpiralLegInfo {
        public int startX;
        public int startZ;
        public int endX;
        public int endZ;
        public int spiralWidth;
        public int spiralHeight;
        public float yaw;
        public boolean mainPath;
        public int legNumber;

        SpiralLegInfo(int sx, int sz, int ex, int ez, int w, int h, float y, boolean mp, int num) {
            this.startX = sx;
            this.startZ = sz;
            this.endX = ex;
            this.endZ = ez;
            this.spiralWidth = w;
            this.spiralHeight = h;
            this.yaw = y;
            this.mainPath = mp;
            this.legNumber = num;
        }

        double distanceToLeg(int px, int pz) {
            double dx = this.endX - this.startX;
            double dz = this.endZ - this.startZ;
            double legLengthSq = dx * dx + dz * dz;
            if (legLengthSq == 0.0) return Math.sqrt((px - this.startX) * (px - this.startX) + (pz - this.startZ) * (pz - this.startZ));
            double t = Math.max(0.0, Math.min(1.0, ((px - this.startX) * dx + (pz - this.startZ) * dz) / legLengthSq));
            double projX = this.startX + t * dx;
            double projZ = this.startZ + t * dz;
            return Math.sqrt((px - projX) * (px - projX) + (pz - projZ) * (pz - projZ));
        }

        int[] projectOntoLeg(int px, int pz) {
            double dx = this.endX - this.startX;
            double dz = this.endZ - this.startZ;
            double legLengthSq = dx * dx + dz * dz;
            if (legLengthSq == 0.0) return new int[]{this.startX, this.startZ};
            double t = ((px - this.startX) * dx + (pz - this.startZ) * dz) / legLengthSq;
            t = Math.max(0.0, Math.min(1.0, t));
            int projX = (int) Math.round(this.startX + t * dx);
            int projZ = (int) Math.round(this.startZ + t * dz);
            return new int[]{projX, projZ};
        }

        boolean isAhead(int currentX, int currentZ, int targetX, int targetZ) {
            double dx = this.endX - this.startX;
            double dz = this.endZ - this.startZ;
            double currentProgress = (currentX - this.startX) * dx + (currentZ - this.startZ) * dz;
            double targetProgress = (targetX - this.startX) * dx + (targetZ - this.startZ) * dz;
            return targetProgress > currentProgress;
        }
    }
}