package com.hunterbuddy.modules.scaffold;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

record PlacementTarget(BlockPos position, BlockHitResult hit) {}
