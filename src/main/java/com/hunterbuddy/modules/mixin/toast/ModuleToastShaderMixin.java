package com.hunterbuddy.modules.mixin.toast;

import com.hunterbuddy.hud.toast.ModuleToastBlur;
import net.minecraft.client.gl.ShaderLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderLoader.class)
public abstract class ModuleToastShaderMixin {
    @Inject(method = "apply(Lnet/minecraft/client/gl/ShaderLoader$Definitions;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
        at = @At("TAIL"))
    private void hunterBuddy$reloadToastBlur(CallbackInfo info) {
        ModuleToastBlur.INSTANCE.invalidate();
    }
}
