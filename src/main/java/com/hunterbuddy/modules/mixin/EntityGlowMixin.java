package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.Shader;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin on {@link Entity#isGlowing()} — when the HunterBuddy Shader module is
 * active and the entity is in its target set, force {@code isGlowing()} to
 * return {@code true} so the vanilla outline render layer fires for it.
 *
 * <p>Yarn note: {@code isGlowing} is stable across 1.20.x and 1.21.x.
 *
 * <p>Sodium does not affect this path (it optimises chunk rendering, not
 * entity rendering). Iris may bypass the vanilla outline pass depending on
 * the active shader pack — documented for the user, not handled here.
 */
@Mixin(Entity.class)
public class EntityGlowMixin {
    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void hb$forceGlowing(CallbackInfoReturnable<Boolean> cir) {
        Shader shader = Modules.get().get(Shader.class);
        if (shader == null || !shader.isActive()) return;
        Entity self = (Entity) (Object) this;
        if (shader.shouldGlow(self)) {
            cir.setReturnValue(true);
        }
    }
}
