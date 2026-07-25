/*
 * Port of com.lambda.interaction.managers.PositionBlocking (lambda 1.21.11).
 */
package com.hunterbuddy.lambda;

import net.minecraft.util.math.BlockPos;
import java.util.List;

public interface PositionBlocking {
    List<BlockPos> getBlockedPositions();
}
