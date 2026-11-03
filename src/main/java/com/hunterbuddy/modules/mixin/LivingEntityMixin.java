package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.AutoFlyingRegear;
import com.hunterbuddy.modules.ElytraBounce;
import com.hunterbuddy.modules.ElytraRecast;
import com.hunterbuddy.modules.Fly300km;
import com.hunterbuddy.modules.NoJumpDelay;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.hunterbuddy.util.CachedModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    private int jumpingCooldown;

    @Shadow
    public abstract Brain<?> getBrain();

    @Unique
    private final CachedModule<ElytraBounce> hunterBuddy$bounce = new CachedModule<>(ElytraBounce.class);
    @Unique
    private final CachedModule<NoJumpDelay> hunterBuddy$noJumpDelay = new CachedModule<>(NoJumpDelay.class);
    @Unique
    private final CachedModule<Fly300km> hunterBuddy$fly300 = new CachedModule<>(Fly300km.class);

    @Inject(at = @At("HEAD"), method = "tickMovement")
    private void hunterBuddy$clearJumpCooldown(CallbackInfo ci) {
        if (hunterBuddy$isLocalPlayer() && hunterBuddy$cooldownBlocked()) {
            this.jumpingCooldown = 0;
        }
    }

    @Inject(at = @At("RETURN"), method = "isGliding", cancellable = true)
    private void hunterBuddy$alterGliding(CallbackInfoReturnable<Boolean> cir) {
        if (!hunterBuddy$isLocalPlayer()) return;
        ElytraBounce bounce = hunterBuddy$bounce.get();
        if (bounce == null || !bounce.enabled()) return;
        boolean real = cir.getReturnValue();
        boolean altered = bounce.modifyIsGliding(real);
        if (altered != real) cir.setReturnValue(altered);
    }

    @WrapOperation(
        method = "calcGlidingVelocity(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F")
    )
    private float hunterBuddy$sustainedPitch(LivingEntity entity, Operation<Float> original) {
        if (entity != MeteorClient.mc.player) return original.call(entity);
        Float pitch = RotationUtils.getInstance().getMovementPitch();
        return pitch != null ? pitch : original.call(entity);
    }

    @WrapOperation(
        method = "travelGliding",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;calcGlidingVelocity(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;"
        )
    )
    private Vec3d hunterBuddy$windowedVelocity(LivingEntity entity, Vec3d oldVelocity, Operation<Vec3d> original) {
        Vec3d vanilla = original.call(entity, oldVelocity);
        if (entity != MeteorClient.mc.player) return vanilla;
        Fly300km fly = hunterBuddy$fly300.get();
        if (fly == null || !fly.isActive()) return vanilla;
        Vec3d ride = fly.windowVelocity(vanilla);
        return ride != null ? ride : vanilla;
    }

    @Unique
    private boolean hunterBuddy$isLocalPlayer() {
        return MeteorClient.mc.player != null && MeteorClient.mc.player.getBrain().equals(this.getBrain());
    }

    @Unique
    private boolean hunterBuddy$cooldownBlocked() {
        ElytraBounce bounce = hunterBuddy$bounce.get();
        NoJumpDelay noJumpDelay = hunterBuddy$noJumpDelay.get();
        ElytraRecast recast = Modules.get().get(ElytraRecast.class);
        AutoFlyingRegear regear = Modules.get().get(AutoFlyingRegear.class);
        return (bounce != null && bounce.enabled())
            || (noJumpDelay != null && noJumpDelay.isActive())
            || (recast != null && recast.isRecovering())
            || (regear != null && regear.isTakingOff());
    }
}
