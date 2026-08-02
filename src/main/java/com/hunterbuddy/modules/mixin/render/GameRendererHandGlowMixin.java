package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.render.HandGlowState;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets the first-person hand submission so {@link HandGlowState} can force
 * an outline colour onto it, and flushes the outline vertex consumers once the
 * hand has been dispatched (which happens in {@code renderWorld}, right after
 * {@code renderHand} returns and before vanilla blits the outline framebuffer).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererHandGlowMixin {
    @Inject(
        method = "renderHand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"
        )
    )
    private void hb$beginHandOutline(CallbackInfo ci) {
        HandGlowState.begin();
    }

    @Inject(
        method = "renderHand",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            shift = At.Shift.AFTER
        )
    )
    private void hb$endHandOutline(CallbackInfo ci) {
        HandGlowState.end();
    }

    @Inject(
        method = "renderWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/command/RenderDispatcher;render()V",
            shift = At.Shift.AFTER
        )
    )
    private void hb$flushHandOutline(CallbackInfo ci) {
        HandGlowState.flush();
    }
}
