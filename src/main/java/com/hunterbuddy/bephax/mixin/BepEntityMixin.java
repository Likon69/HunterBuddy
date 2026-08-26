package com.hunterbuddy.bephax.mixin;

import com.hunterbuddy.bephax.BepRotations;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Makes the client fly the steered rotation, not just look at it.
 *
 * <p>The other half of the rotation system. The wire mixin tells the server where you are
 * pointing; this one tells the physics. Without it the glide takes its direction from the
 * camera -- {@code getRotationVector()} reads the real yaw and pitch -- so a solved dive of
 * twenty-six degrees only changes the lift term and the flight carries straight on. The
 * camera stays yours; only what the movement reads is redirected.
 */
@Mixin(Entity.class)
public abstract class BepEntityMixin {
    @WrapOperation(method = "getRotationVector()Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    private float bephax$rotationVectorYaw(Entity entity, Operation<Float> original) {
        if ((Object) this != MeteorClient.mc.player) return (Float) original.call(new Object[]{entity});

        Float yaw = BepRotations.getInstance().getMovementYaw();

        return yaw == null ? (Float) original.call(new Object[]{entity}) : yaw;
    }

    @WrapOperation(method = "getRotationVector()Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch()F"))
    private float bephax$rotationVectorPitch(Entity entity, Operation<Float> original) {
        if ((Object) this != MeteorClient.mc.player) return (Float) original.call(new Object[]{entity});

        Float pitch = BepRotations.getInstance().getMovementPitch();

        return pitch == null ? (Float) original.call(new Object[]{entity}) : pitch;
    }

    @WrapOperation(method = "getRotationVec(F)Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
    private float bephax$rotationVecYaw(Entity entity, float tickDelta, Operation<Float> original) {
        if ((Object) this != MeteorClient.mc.player) return (Float) original.call(new Object[]{entity, tickDelta});

        Float yaw = BepRotations.getInstance().getMovementYaw();

        return yaw == null ? (Float) original.call(new Object[]{entity, tickDelta}) : yaw;
    }

    @WrapOperation(method = "getRotationVec(F)Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
    private float bephax$rotationVecPitch(Entity entity, float tickDelta, Operation<Float> original) {
        if ((Object) this != MeteorClient.mc.player) return (Float) original.call(new Object[]{entity, tickDelta});

        Float pitch = BepRotations.getInstance().getMovementPitch();

        return pitch == null ? (Float) original.call(new Object[]{entity, tickDelta}) : pitch;
    }

    @WrapOperation(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    private float bephax$velocityYaw(Entity entity, Operation<Float> original) {
        if ((Object) this != MeteorClient.mc.player) return (Float) original.call(new Object[]{entity});

        Float yaw = BepRotations.getInstance().getMoveYaw();

        return yaw == null ? (Float) original.call(new Object[]{entity}) : yaw;
    }

    @ModifyVariable(method = "updateVelocity", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float bephax$matchDeclaredInputMagnitude(float speed) {
        return (Object) this != MeteorClient.mc.player ? speed : speed * BepRotations.getInstance().getMoveSpeedScale();
    }
}
