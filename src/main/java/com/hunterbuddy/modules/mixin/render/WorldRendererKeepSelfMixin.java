package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.modules.FlightTrail;
import com.hunterbuddy.modules.Shader;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererKeepSelfMixin {
    @ModifyExpressionValue(
        method = "fillEntityRenderStates",
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderManager;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"
        )
    )
    private boolean hb$keepSelfPastCulling(boolean original, @Local Entity entity, @Local(argsOnly = true) Camera camera) {
        return original || hb$wanted(entity, camera);
    }

    @ModifyExpressionValue(
        method = "fillEntityRenderStates",
        require = 0,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;isRenderingReady(Lnet/minecraft/util/math/BlockPos;)Z"
        )
    )
    private boolean hb$keepSelfInUnbuiltSections(boolean original, @Local Entity entity, @Local(argsOnly = true) Camera camera) {
        return original || hb$wanted(entity, camera);
    }

    private static boolean hb$wanted(Entity entity, Camera camera) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (entity == null || entity != mc.player || !camera.isThirdPerson()) return false;
        Shader shader = Modules.get().get(Shader.class);
        if (shader != null && shader.isActive() && shader.selfEnabled()) return true;
        FlightTrail flightTrail = Modules.get().get(FlightTrail.class);
        return flightTrail != null && flightTrail.isActive() && mc.player.isGliding();
    }
}
