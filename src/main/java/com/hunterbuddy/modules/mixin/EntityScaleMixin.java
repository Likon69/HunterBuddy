package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.EntityScale;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies {@link EntityScale} by multiplying {@code LivingEntityRenderState#baseScale}.
 *
 * <p>That field is the one vanilla feeds straight into {@code matrices.scale()}
 * at the top of {@code LivingEntityRenderer#render}, before the overridable
 * {@code scale()} hook runs — so every living entity is covered, including the
 * ones whose renderer replaces {@code scale()} without calling {@code super}
 * (players, slimes, magma cubes). It also feeds {@code getShadowRadius}, so the
 * drop shadow follows the model instead of staying the old size.
 *
 * <p>{@code updateRenderState} reassigns {@code baseScale} from
 * {@code entity.getScale()} on every frame, so multiplying at RETURN cannot
 * compound. This is the same mechanism Meteor's own Chams uses for its player
 * scale.
 *
 * <p>Nothing is drawn here and no matrix is pushed: an earlier draft wrapped
 * {@code render} in push/scale…pop, which breaks the matrix stack whenever
 * another mixin cancels that method at HEAD after ours ran — Meteor's Chams
 * mixin does exactly that for {@code NoRender.noDeadEntities()}.
 */
@Mixin(LivingEntityRenderer.class)
public class EntityScaleMixin {

    @Inject(
        method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
        at = @At("RETURN")
    )
    private void hb$applyScale(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo ci) {
        EntityScale module = Modules.get().get(EntityScale.class);
        if (module == null || !module.isActive()) return;

        float scale;
        if (entity instanceof PlayerEntity) {
            scale = module.getPlayerScale(entity == MinecraftClient.getInstance().player);
        } else if (entity instanceof MobEntity) {
            scale = module.getMobScale();
        } else {
            return; // armour stands and other non-mob living entities are left alone
        }

        if (scale <= 0.0f || scale == 1.0f) return;

        state.baseScale *= scale;

        // Keep the vanilla nametag sitting just above the head instead of inside it.
        if (state.nameLabelPos != null) {
            state.nameLabelPos = state.nameLabelPos.add(0.0, entity.getHeight() * (scale - 1.0f), 0.0);
        }
    }
}
