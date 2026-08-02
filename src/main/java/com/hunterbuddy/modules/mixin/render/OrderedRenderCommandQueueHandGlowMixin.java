package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.render.HandGlowState;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Rewrites the {@code outlineColor} of render commands submitted while the
 * first-person hand is being drawn — see {@link HandGlowState}. Outside that
 * window every call is a no-op passthrough.
 *
 * <p>Ordinals count {@code int} arguments only:
 * <ul>
 *   <li>{@code submitItem(.., light, overlay, outlineColors, ..)} -> 2</li>
 *   <li>{@code submitModel(.., light, overlay, tintedColor, .., outlineColor, ..)} -> 3</li>
 *   <li>{@code submitModelPart(.., light, overlay, .., tintedColor, .., outlineColor)} -> 3</li>
 * </ul>
 */
@Mixin(OrderedRenderCommandQueueImpl.class)
public abstract class OrderedRenderCommandQueueHandGlowMixin {
    @ModifyVariable(method = "submitItem", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int hb$itemOutlineColor(int outlineColors) {
        return HandGlowState.outlineColor(outlineColors);
    }

    @ModifyVariable(method = "submitModel", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private int hb$modelOutlineColor(int outlineColor) {
        return HandGlowState.outlineColor(outlineColor);
    }

    @ModifyVariable(method = "submitModelPart", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private int hb$modelPartOutlineColor(int outlineColor) {
        return HandGlowState.outlineColor(outlineColor);
    }
}
