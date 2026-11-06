package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.render.SkyboxState;
import com.hunterbuddy.render.SkyboxUniforms;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererSkyboxMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void hb$captureSkybox(ObjectAllocator allocator,
                                  RenderTickCounter tickCounter,
                                  boolean renderBlockOutline,
                                  Camera camera,
                                  Matrix4f positionMatrix,
                                  Matrix4f projectionMatrix,
                                  Matrix4f matrix4f2,
                                  GpuBufferSlice fog,
                                  Vector4f fogColor,
                                  boolean shouldRenderSky,
                                  CallbackInfo ci) {
        SkyboxUniforms.flipFrame();
        SkyboxState.capture(positionMatrix, projectionMatrix, fogColor);
    }
}
