/*
 * Port of com.lambda.interaction.construction.blueprint.typealias.Structure
 * (lambda 1.21.11). Lambda uses `typealias Structure = Map<BlockPos, TargetState>`
 * — a simple mapping from block positions to their target states.
 */
package com.hunterbuddy.lambda;

import net.minecraft.util.math.BlockPos;
import java.util.Map;

public interface Structure extends Map<BlockPos, TargetState> {
}
