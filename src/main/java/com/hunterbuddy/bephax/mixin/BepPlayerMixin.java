package com.hunterbuddy.bephax.mixin;

import com.hunterbuddy.bephax.BepRotations;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Puts the steered rotation into the movement packets.
 *
 * <p>Without it the server is told the camera's rotation while the client flies the steered
 * one, and anything predicted from the rotation it received -- the anticheat's idea of where
 * the next tick should land above all -- is computed from a manoeuvre that never happened.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class BepPlayerMixin {
    @WrapOperation(
        method = "sendMovementPackets",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F")
    )
    private float bephax$sentYaw(ClientPlayerEntity player, Operation<Float> original) {
        BepRotations rotations = BepRotations.getInstance();

        return rotations.isRotating() || rotations.isWireFresh()
            ? rotations.getSentYaw()
            : (Float) original.call(new Object[]{player});
    }

    @WrapOperation(
        method = "sendMovementPackets",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F")
    )
    private float bephax$sentPitch(ClientPlayerEntity player, Operation<Float> original) {
        BepRotations rotations = BepRotations.getInstance();

        return rotations.isRotating() || rotations.isWireFresh()
            ? rotations.getSentPitch()
            : (Float) original.call(new Object[]{player});
    }
}
