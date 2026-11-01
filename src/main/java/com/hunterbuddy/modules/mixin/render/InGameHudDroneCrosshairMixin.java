package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.modules.DroneCamera;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudDroneCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void hb$hideCrosshairWhileFilming(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        DroneCamera droneCamera = Modules.get().get(DroneCamera.class);
        if (droneCamera != null && droneCamera.isFilming()) ci.cancel();
    }
}
