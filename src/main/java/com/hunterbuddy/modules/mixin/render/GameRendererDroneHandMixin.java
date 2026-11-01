package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.modules.DroneCamera;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererDroneHandMixin {
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void hb$hideHandWhileFilming(CallbackInfo ci) {
        DroneCamera droneCamera = Modules.get().get(DroneCamera.class);
        if (droneCamera != null && droneCamera.isFilming()) ci.cancel();
    }
}
