package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.ElytraBounce;
import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = Camera.class, priority = 1001)
public class ElytraBounceCameraMixin {
    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void hunterBuddy$modifyCameraRotation(Args args, @Local(argsOnly = true) Entity focusedEntity) {
        FreeLook freeLook = Modules.get().get(FreeLook.class);
        if (freeLook != null && freeLook.isActive()) return;

        ElytraBounce elytraBounce = Modules.get().get(ElytraBounce.class);
        if (elytraBounce != null && elytraBounce.isFreePitchEnabled() && focusedEntity != null) {
            args.set(1, elytraBounce.cameraPitch);
        }
    }
}
