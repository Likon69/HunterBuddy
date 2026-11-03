package com.hunterbuddy.modules.rotationdetector;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

final class SidewaysBlockMatcher {
    private final RotationDetectorOptions options;

    SidewaysBlockMatcher(RotationDetectorOptions options) {
        this.options = options;
    }

    boolean isHorizontalUnnatural(BlockState state) {
        if (!state.contains(Properties.AXIS)) return false;
        if (state.get(Properties.AXIS) == Direction.Axis.Y) return false;
        return isEnabledLog(state) || isEnabledBasalt(state) || isEnabledPurpur(state)
            || isEnabledHay(state) || isEnabledDeepslate(state);
    }

    private boolean isEnabledLog(BlockState state) {
        return options.logs.get() && (state.isIn(BlockTags.LOGS) || state.isOf(Blocks.BAMBOO_BLOCK));
    }

    private boolean isEnabledBasalt(BlockState state) {
        return options.basalt.get() && (state.isOf(Blocks.BASALT) || state.isOf(Blocks.POLISHED_BASALT));
    }

    private boolean isEnabledPurpur(BlockState state) {
        return options.purpurPillars.get() && state.isOf(Blocks.PURPUR_PILLAR);
    }

    private boolean isEnabledHay(BlockState state) {
        return options.hayBlocks.get() && state.isOf(Blocks.HAY_BLOCK);
    }

    private boolean isEnabledDeepslate(BlockState state) {
        return options.deepslate.get() && state.isOf(Blocks.DEEPSLATE);
    }
}
