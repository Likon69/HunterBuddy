package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.render.SkyboxRenderer;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public abstract class SkyRenderingSkyboxMixin {
    @Inject(method = "renderTopSky", at = @At("HEAD"), cancellable = true)
    private void hb$replaceTopSky(int skyColor, CallbackInfo ci) {
        if (SkyboxRenderer.replacesOverworld() && SkyboxRenderer.draw(false)) ci.cancel();
    }

    @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true)
    private void hb$hideGlowingSky(MatrixStack matrices, float sunAngle, int color, CallbackInfo ci) {
        if (SkyboxRenderer.replacesOverworld()) ci.cancel();
    }

    @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true)
    private void hb$hideSkyDark(CallbackInfo ci) {
        if (SkyboxRenderer.replacesOverworld()) ci.cancel();
    }

    @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true)
    private void hb$hideCelestialBodies(MatrixStack matrices, float sunAngle, float moonAngle, float starAngle,
                                        MoonPhase moonPhase, float rainGradient, float starBrightness, CallbackInfo ci) {
        if (SkyboxRenderer.hidesCelestialBodies()) ci.cancel();
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void hb$replaceEndSky(CallbackInfo ci) {
        if (SkyboxRenderer.replacesEnd() && SkyboxRenderer.draw(true)) ci.cancel();
    }
}
