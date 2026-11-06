package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.render.SkyboxRenderer;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererSkyboxMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void hb$hideClouds(int color, CloudRenderMode mode, float cloudHeight, Vec3d cameraPos,
                               long time, float tickProgress, CallbackInfo ci) {
        if (SkyboxRenderer.hidesClouds()) ci.cancel();
    }
}
