package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.modules.Shader;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityOutlineAlphaMixin {
    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void hb$applyOutlineAlpha(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (state.outlineColor == 0) return;

        Shader shader = Modules.get().get(Shader.class);
        if (shader == null || !shader.isActive() || !shader.shouldGlow(entity)) return;

        state.outlineColor = shader.outlineArgb(entity);
    }
}
