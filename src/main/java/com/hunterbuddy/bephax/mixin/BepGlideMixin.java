package com.hunterbuddy.bephax.mixin;

import com.hunterbuddy.bephax.BepBoost;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The reference's glide hook.
 *
 * <p>It hands the whole glide step to the boost, which declares the fastest velocity the
 * server will still accept instead of the one vanilla would have built up to.
 *
 * <p>Making that step read the steered pitch rather than the camera's is the other half of
 * the reference, and it is not done here. {@code LivingEntityMixin} was already wrapping that
 * exact call for the elytra modules, and a second wrapper on one instruction, from a second
 * mixin config with nothing to order the two by, cost elytra bounce the hooks that live in
 * that same mixin. It asks both rotation systems instead.
 */
@Mixin(LivingEntity.class)
public abstract class BepGlideMixin {
    @WrapOperation(
        method = "travelGliding(Lnet/minecraft/util/math/Vec3d;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;calcGlidingVelocity(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;")
    )
    private Vec3d bephax$rocketBoost(LivingEntity entity, Vec3d oldVelocity, Operation<Vec3d> original) {
        BepBoost boost = Modules.get().get(BepBoost.class);

        if (boost != null && boost.isActive() && entity == MeteorClient.mc.player) {
            Vec3d vanilla = (Vec3d) original.call(new Object[]{entity, oldVelocity});
            Vec3d boosted = boost.glideVelocity(oldVelocity, vanilla);

            return boosted != null ? boosted : vanilla;
        }

        return (Vec3d) original.call(new Object[]{entity, oldVelocity});
    }
}
