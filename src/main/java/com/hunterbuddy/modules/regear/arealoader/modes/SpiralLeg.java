package com.hunterbuddy.modules.regear.arealoader.modes;

import com.hunterbuddy.modules.regear.arealoader.Heading;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;

record SpiralLeg(BlockPos start, int width, int height, float yaw, boolean mainPath) {
    private static final float QUARTER_TURN = 90.0f;

    int length(int blockGap) {
        return blockGap + (mainPath ? width : height);
    }

    BlockPos end(int blockGap) {
        Heading heading = Heading.of(yaw);
        int length = length(blockGap);
        return start.add(heading.stepX() * length, 0, heading.stepZ() * length);
    }

    SpiralLeg next(int blockGap) {
        float turned = Heading.normalize(yaw + QUARTER_TURN);
        BlockPos corner = end(blockGap);
        if (mainPath) return new SpiralLeg(corner, width + blockGap, height, turned, false);
        return new SpiralLeg(corner, width, height + blockGap, turned, true);
    }

    SpiralLeg startingAt(BlockPos newStart) {
        return new SpiralLeg(newStart, width, height, yaw, mainPath);
    }

    Optional<SpiralLeg> previous(int blockGap) {
        int previousWidth = mainPath ? width : width - blockGap;
        int previousHeight = mainPath ? height - blockGap : height;
        if (previousWidth < 0 || previousHeight < 0) return Optional.empty();
        float previousYaw = Heading.normalize(yaw - QUARTER_TURN);
        int travelled = mainPath ? height : width;
        Heading heading = Heading.of(previousYaw);
        BlockPos previousStart = start.add(-heading.stepX() * travelled, 0, -heading.stepZ() * travelled);
        return Optional.of(new SpiralLeg(previousStart, previousWidth, previousHeight, previousYaw, !mainPath));
    }
}
