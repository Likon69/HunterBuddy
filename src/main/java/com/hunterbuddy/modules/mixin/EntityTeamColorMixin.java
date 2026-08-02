package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.Shader;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin on {@link Entity#getTeamColorValue()} — when the HunterBuddy Shader
 * module is active and the entity is in its target set, return the configured
 * outline colour (RGB-packed, alpha omitted) so the vanilla outline shader
 * draws the silhouette in our colour.
 *
 * <p>Yarn 1.21.1: {@code class_1297.method_5746()} (stable name in this
 * version). If this Mixin fails to apply after a Yarn mapping bump, re-check
 * the intermediary name — do NOT guess.
 */
@Mixin(Entity.class)
public class EntityTeamColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void hb$forceTeamColor(CallbackInfoReturnable<Integer> cir) {
        Shader shader = Modules.get().get(Shader.class);
        if (shader == null || !shader.isActive()) return;
        Entity self = (Entity) (Object) this;
        if (shader.shouldGlow(self)) {
            cir.setReturnValue(shader.outlineRgb(self));
        }
    }
}
