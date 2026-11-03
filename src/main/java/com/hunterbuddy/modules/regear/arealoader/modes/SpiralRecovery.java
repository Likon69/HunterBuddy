package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.regear.arealoader.AreaLoader;
import com.hunterbuddy.modules.regear.arealoader.AreaSaveFiles;
import com.hunterbuddy.modules.regear.arealoader.Heading;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.math.BlockPos;

final class SpiralRecovery {
    private static final double ACCEPT_FRACTION = 0.5;
    private static final double APPLY_LIMIT_FACTOR = 2.0;
    private static final double EXACT_RESUME_DISTANCE = 50.0;

    private final AreaLoader area;
    private final File saveFile;
    private final Consumer<String> log;

    SpiralRecovery(AreaLoader area, File saveFile, Consumer<String> log) {
        this.area = area;
        this.saveFile = saveFile;
        this.log = log;
    }

    boolean isNearPath(BlockPos origin, BlockPos target, int gap) {
        Spiral.SpiralLegInfo leg = describeClosest(origin, target, gap);
        return distance(leg, target) < gap * ACCEPT_FRACTION;
    }

    boolean saveResumePoint(BlockPos origin, BlockPos target, int gap) {
        Spiral.SpiralLegInfo leg = describeClosest(origin, target, gap);
        double distance = distance(leg, target);
        if (distance > APPLY_LIMIT_FACTOR * gap) {
            ChatUtils.error("The position is %d blocks away from the spiral path, too far to resume from", Math.round(distance));
            return false;
        }
        BlockPos resume = distance < EXACT_RESUME_DISTANCE ? atPathLevel(target.getX(), target.getZ()) : projection(leg, target);
        BlockPos legStart = atPathLevel(leg.startX, leg.startZ);
        return write(origin, new Spiral.PathingDataSpiral(legStart, resume, leg.yaw, leg.mainPath, leg.spiralWidth, leg.spiralHeight));
    }

    int[] nearestPathPoint(BlockPos origin, BlockPos target, int gap) {
        Spiral.SpiralLegInfo leg = new SpiralLegSearch(origin, gap, log).findClosest(target);
        BlockPos point = projection(leg, target);
        return new int[]{point.getX(), point.getZ()};
    }

    int[] nextCorner(BlockPos origin, BlockPos target, int gap) {
        Spiral.SpiralLegInfo leg = new SpiralLegSearch(origin, gap, log).findClosest(target);
        return new int[]{leg.endX, leg.endZ};
    }

    boolean saveFromNextCorner(BlockPos origin, BlockPos target, int gap) {
        Spiral.SpiralLegInfo leg = new SpiralLegSearch(origin, gap, log).findClosest(target);
        SpiralLeg following = leg.leg().next(gap);
        BlockPos corner = following.start();
        Spiral.PathingDataSpiral data = new Spiral.PathingDataSpiral(corner, corner, following.yaw(), following.mainPath(),
            following.width(), following.height());
        if (!write(origin, data)) return false;
        ChatUtils.info("Fly to %s, the spiral will then head %s", area.coords(corner.getX(), corner.getZ()),
            directionName(following.yaw()));
        return true;
    }

    private Spiral.SpiralLegInfo describeClosest(BlockPos origin, BlockPos target, int gap) {
        Spiral.SpiralLegInfo leg = new SpiralLegSearch(origin, gap, log).findClosest(target);
        log.accept("Leg " + leg.legNumber + " from " + area.coords(leg.startX, leg.startZ) + " to "
            + area.coords(leg.endX, leg.endZ) + ", heading " + directionName(leg.yaw));
        log.accept("Size " + leg.spiralWidth + "x" + leg.spiralHeight + ", " + Math.round(distance(leg, target))
            + " blocks from the path");
        return leg;
    }

    private static double distance(Spiral.SpiralLegInfo leg, BlockPos target) {
        return leg.segment().distanceTo(target.getX(), target.getZ());
    }

    private static BlockPos projection(Spiral.SpiralLegInfo leg, BlockPos target) {
        Segment segment = leg.segment();
        double[] point = segment.pointAt(segment.fractionNearest(target.getX(), target.getZ()));
        return atPathLevel((int) Math.round(point[0]), (int) Math.round(point[1]));
    }

    private static BlockPos atPathLevel(int x, int z) {
        return new BlockPos(x, SpiralLegSearch.PATH_Y, z);
    }

    private boolean write(BlockPos origin, Spiral.PathingDataSpiral data) {
        if (saveFile == null) {
            ChatUtils.error("Cannot build the spiral save path, the dimension is unknown");
            return false;
        }
        data.spiralOrigin = atPathLevel(origin.getX(), origin.getZ());
        try {
            AreaSaveFiles.writeJson(saveFile, data);
            return true;
        } catch (IOException e) {
            ChatUtils.error("Could not write the spiral save: %s", e.getMessage());
            return false;
        }
    }

    private static String directionName(float yaw) {
        return switch (Heading.of(yaw)) {
            case SOUTH -> "South (+Z)";
            case WEST -> "West (-X)";
            case NORTH -> "North (-Z)";
            case EAST -> "East (+X)";
        };
    }
}
