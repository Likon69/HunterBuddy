package com.hunterbuddy.bephax.mixin;

import com.hunterbuddy.bephax.BepBoost;
import com.hunterbuddy.bephax.BepRotations;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The reference's two glide hooks.
 *
 * <p>The first hands the whole glide step to the boost, which declares the fastest velocity
 * the server will still accept instead of the one vanilla would have built up to. The second
 * makes the step read the steered pitch rather than the camera's, so a swing is flown as a
 * swing.
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

    @WrapOperation(
        method = "calcGlidingVelocity(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F")
    )
    private float bephax$wrapGlidingPitch(LivingEntity entity, Operation<Float> original) {
        Float pitch = BepRotations.getInstance().getMovementPitch();

        return entity == MeteorClient.mc.player && pitch != null ? pitch : (Float) original.call(new Object[]{entity});
    }
}
