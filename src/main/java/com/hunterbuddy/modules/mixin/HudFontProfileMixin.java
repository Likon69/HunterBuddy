package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.hud.text.HudFontProfile;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HudRenderer.class, remap = false)
public abstract class HudFontProfileMixin {
    @Unique private static long hunterBuddy$fontStarted;

    @Inject(method = "loadFont", at = @At("HEAD"))
    private static void hunterBuddy$fontStart(int height, CallbackInfoReturnable<Object> info) {
        hunterBuddy$fontStarted = System.nanoTime();
    }

    @Inject(method = "loadFont", at = @At("RETURN"))
    private static void hunterBuddy$fontEnd(int height, CallbackInfoReturnable<Object> info) {
        HudFontProfile.loaded(System.nanoTime() - hunterBuddy$fontStarted);
    }

    @Inject(method = "begin", at = @At("HEAD"))
    private void hunterBuddy$frame(DrawContext context, CallbackInfo info) {
        HudFontProfile.frame();
    }
}
