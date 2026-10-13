package com.hunterbuddy.behavior.farmer;

import java.util.Objects;
import net.minecraft.util.math.BlockPos;

public record FarmerStepConfig(int index, boolean enabled, String name, String profile, int shulkerTarget, BlockPos deliveryChest) {
    public FarmerStepConfig {
        if (index < 1) throw new IllegalArgumentException("step index must be positive");
        Objects.requireNonNull(name);
        Objects.requireNonNull(profile);
        Objects.requireNonNull(deliveryChest);
    }
}
