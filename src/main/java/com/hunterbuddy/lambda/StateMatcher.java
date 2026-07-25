/*
 * Port of com.lambda.interaction.construction.verify.StateMatcher
 * (lambda 1.21.11) — direct 1:1 translation to Java.
 */
package com.hunterbuddy.lambda;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import java.util.Collection;

public interface StateMatcher {
    boolean matches(BlockState state, BlockPos pos, Collection<Property<?>> ignoredProperties);
    ItemStack getStack(BlockPos pos);
    BlockState getState(BlockPos pos);
    boolean isEmpty();
}
