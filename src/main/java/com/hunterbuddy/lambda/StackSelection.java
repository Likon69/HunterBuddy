/*
 * Port of com.lambda.interaction.material.StackSelection (lambda 1.21.11)
 */
package com.hunterbuddy.lambda;

import net.minecraft.item.ItemStack;

public interface StackSelection {
    boolean filterStack(ItemStack stack);
}
