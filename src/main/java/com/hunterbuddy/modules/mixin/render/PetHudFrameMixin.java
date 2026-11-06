package com.hunterbuddy.modules.mixin.render;

import com.hunterbuddy.pet.PetHudCapture;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(InGameHud.class)
public abstract class PetHudFrameMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private DrawContext hunterbuddy$beginPetSurfaces(DrawContext context) {
        PetHudCapture.begin();
        return context;
    }
}
