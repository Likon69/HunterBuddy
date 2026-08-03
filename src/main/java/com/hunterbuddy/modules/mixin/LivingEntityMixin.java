package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.AutoFlyingRegear;
import com.hunterbuddy.modules.ElytraBounce;
import com.hunterbuddy.modules.ElytraRecast;
import com.hunterbuddy.modules.NoJumpDelay;
import com.hunterbuddy.modules.regear.util.RotationUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
   @Shadow
   private int jumpingCooldown;
   private Module noJumpDelay;
   private ElytraBounce efly;

   @Shadow
   public abstract Brain<?> getBrain();

   private Module getNoJumpDelay() {
      if (this.noJumpDelay == null) {
         this.noJumpDelay = Modules.get().get(NoJumpDelay.class);
      }

      return this.noJumpDelay;
   }

   private ElytraBounce getEfly() {
      if (this.efly == null) {
         this.efly = (ElytraBounce)Modules.get().get(ElytraBounce.class);
      }

      return this.efly;
   }

   @Inject(at = @At("HEAD"), method = "tickMovement")
   private void tickMovement(CallbackInfo ci) {
      ElytraBounce eflyModule = this.getEfly();
      Module noJumpDelayModule = this.getNoJumpDelay();
      ElytraRecast elytraRecast = (ElytraRecast)Modules.get().get(ElytraRecast.class);
      AutoFlyingRegear autoFlyingRegear = (AutoFlyingRegear)Modules.get().get(AutoFlyingRegear.class);
      if (MeteorClient.mc.player != null
         && MeteorClient.mc.player.getBrain().equals(this.getBrain())
         && ((eflyModule != null && eflyModule.enabled())
            || (noJumpDelayModule != null && noJumpDelayModule.isActive())
            || (elytraRecast != null && elytraRecast.isRecovering())
            || (autoFlyingRegear != null && autoFlyingRegear.isTakingOff()))) {
         this.jumpingCooldown = 0;
      }
   }

   @Inject(at = @At("RETURN"), method = "isGliding", cancellable = true)
   private void isGliding(CallbackInfoReturnable<Boolean> cir) {
      ElytraBounce eflyModule = this.getEfly();
      if (MeteorClient.mc.player != null
         && MeteorClient.mc.player.getBrain().equals(this.getBrain())
         && eflyModule != null
         && eflyModule.enabled()
         && !eflyModule.isFakeFlyEnabled()) {
         // Injecting at RETURN gives us the real flag value; the module latches it
         // (Lambda-style) instead of blindly forcing true.
         boolean modified = eflyModule.modifyIsGliding(cir.getReturnValueZ());
         if (modified != cir.getReturnValueZ()) {
            cir.setReturnValue(modified);
         }
      }
   }

   @WrapOperation(
      method = "calcGlidingVelocity(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F")
   )
   private float mlep$wrapGlidingPitch(LivingEntity entity, Operation<Float> original) {
      Float pitch = RotationUtils.getInstance().getMovementPitch();
      return entity == MeteorClient.mc.player && pitch != null ? pitch : (Float)original.call(new Object[]{entity});
   }
}
