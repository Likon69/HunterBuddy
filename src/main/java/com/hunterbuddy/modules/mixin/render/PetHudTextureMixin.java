package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.pet.PetHudCapture;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DrawContext.class)
public abstract class PetHudTextureMixin {
    @ModifyVariable(method = "drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V",
        at = @At("HEAD"), argsOnly = true)
    private Identifier hunterbuddy$petSurface(Identifier sprite, RenderPipeline pipeline, Identifier original,
                                              int x, int y, int width, int height, int color) {
        PetHudCapture.texture((DrawContext) (Object) this, sprite, x, y, width, height);
        return sprite;
    }
}
