package com.hunterbuddy.modules.mixin.accessors;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererFovAccessor {
    @Invoker("getFov")
    float hb$getFov(Camera camera, float tickProgress, boolean changingFov);
}
