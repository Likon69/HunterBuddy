package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.render.HbRenderPipelines;
import net.minecraft.client.gl.ShaderLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Compiles the HunterBuddy render pipelines whenever resources are (re)loaded. */
@Mixin(ShaderLoader.class)
public abstract class ShaderLoaderGlowMixin {
    @Inject(
        method = "apply(Lnet/minecraft/client/gl/ShaderLoader$Definitions;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
        at = @At("TAIL")
    )
    private void hb$reloadPipelines(CallbackInfo info) {
        HbRenderPipelines.precompile();
    }
}
