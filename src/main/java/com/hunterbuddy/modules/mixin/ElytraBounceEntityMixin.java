package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.ElytraBounce;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, priority = 1001)
public abstract class ElytraBounceEntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void hunterBuddy$onChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this != MeteorClient.mc.player) return;

        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook != null && freeLook.isActive()) return;

        ElytraBounce elytraBounce = Modules.get().get(ElytraBounce.class);
        if (elytraBounce != null && elytraBounce.isFreePitchEnabled()) {
            elytraBounce.cameraPitch += (float) (cursorDeltaY * 0.15);
            elytraBounce.cameraPitch = MathHelper.clamp(elytraBounce.cameraPitch, -90.0F, 90.0F);
            MeteorClient.mc.player.setYaw(MeteorClient.mc.player.getYaw() + (float) (cursorDeltaX * 0.15));
            ci.cancel();
        }
    }
}
