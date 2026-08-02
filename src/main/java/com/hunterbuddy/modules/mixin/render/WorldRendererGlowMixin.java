package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.render.HbGlowShader;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.WorldRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Grabs the entity silhouettes for {@link HbGlowShader}.
 *
 * <p>Timing matters: vanilla's {@code entity_outline} post effect (sobel + box
 * blur) writes its result <em>back into</em> {@code minecraft:entity_outline},
 * so by the time the world render returns, that framebuffer holds the thin
 * outline instead of the filled silhouettes. We therefore copy it right after
 * the outline vertex consumers are flushed and before the post effect runs.
 *
 * <p>{@code method_62214} is the main frame-graph pass lambda — an unmapped
 * synthetic, which is why it is referenced by its intermediary name (Meteor's
 * own {@code WorldRendererMixin} hooks the same call site the same way).
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererGlowMixin {
    @Shadow
    private @Nullable Framebuffer entityOutlineFramebuffer;

    @Inject(
        method = "method_62214",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/OutlineVertexConsumerProvider;draw()V",
            shift = At.Shift.AFTER
        )
    )
    private void hb$captureSilhouettes(CallbackInfo ci) {
        HbGlowShader.captureFrom(entityOutlineFramebuffer);
    }
}
