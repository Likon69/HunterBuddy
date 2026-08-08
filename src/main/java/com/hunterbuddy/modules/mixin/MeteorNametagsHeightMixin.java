package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.EntityView;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Nametags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Moves Meteor's nametags up or down with a model rescaled by {@link EntityView}.
 *
 * <p>Meteor anchors a nametag at {@code eyeHeight + 0.5} above the entity's feet.
 * The eye height comes from the hitbox, which we never touch, so without this the
 * tag of a mob drawn at 3x sits somewhere around its waist.
 *
 * <p>Only the eye height is scaled; the {@code 0.5} is a fixed screen-space
 * margin Meteor adds on top and has no reason to grow with the model. The value
 * is recomputed from the entity rather than derived from the original return, so
 * a change to that constant upstream cannot silently skew the result.
 *
 * <p>{@code remap = false}: {@code getHeight} belongs to Meteor, not Minecraft,
 * so it must not go through the Yarn remapper.
 */
@Mixin(value = Nametags.class, remap = false)
public class MeteorNametagsHeightMixin {

    @Inject(method = "getHeight", at = @At("RETURN"), cancellable = true)
    private void hb$followScale(Entity entity, CallbackInfoReturnable<Double> cir) {
        if (!(entity instanceof LivingEntity living)) return;

        EntityView view = Modules.get().get(EntityView.class);
        if (view == null || !view.isActive() || !view.nametagsFollowScale()) return;

        float scale = view.getScaleFor(living);
        if (scale <= 0.0f || scale == 1.0f) return;

        cir.setReturnValue(entity.getEyeHeight(entity.getPose()) * scale + 0.5);
    }
}
