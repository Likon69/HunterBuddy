package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.render.SpeedBlurPass;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererSpeedBlurMixin {
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/fog/FogRenderer;rotate()V"
        )
    )
    private void hb$speedBlur(CallbackInfo ci) {
        SpeedBlurPass.apply();
    }
}
