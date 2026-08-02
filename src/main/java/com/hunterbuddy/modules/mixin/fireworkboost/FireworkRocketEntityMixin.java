package com.hunterbuddy.modules.mixin.fireworkboost;

import com.hunterbuddy.modules.RocketBoost;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {
   @Shadow
   private @Nullable LivingEntity shooter;

   @Unique
   private @Nullable RocketBoost rb;

   @Inject(method = "tick", at = @At("HEAD"))
   private void trackRocketForBoost(CallbackInfo ci) {
      if (this.shooter == null) return;
      if (MeteorClient.mc.player == null) return;
      if (!this.shooter.getUuid().equals(MeteorClient.mc.player.getUuid())) return;
      if (!MeteorClient.mc.player.isGliding()) return;

      FireworkRocketEntity self = (FireworkRocketEntity)(Object) this;
      if (this.rb == null) this.rb = Modules.get().get(RocketBoost.class);
      if (this.rb != null && this.rb.isActive()) {
         this.rb.setTrackedRocket(self.getId());
      }
   }

   @Inject(method = "explodeAndRemove", at = @At("HEAD"), cancellable = true)
   private void cancelExplode(CallbackInfo ci) {
      if (this.rb == null) this.rb = Modules.get().get(RocketBoost.class);
      if (this.rb != null && this.rb.isActive() && this.rb.isBoosting()
         && this.rb.trackedRocketId == ((FireworkRocketEntity)(Object) this).getId()) {
         ci.cancel();
      }
   }

   @ModifyConstant(method = "tick", constant = @Constant(doubleValue = 1.5))
   private double boostFireworkSpeed(double original) {
      if (this.rb == null) this.rb = Modules.get().get(RocketBoost.class);
      if (this.rb != null && this.rb.isActive()
         && this.shooter != null && MeteorClient.mc.player != null
         && this.shooter.getUuid().equals(MeteorClient.mc.player.getUuid())) {
         double desired = this.rb.getSpeed();
         net.minecraft.util.math.Vec3d look = this.shooter.getRotationVector();
         double maxComponent = Math.max(Math.abs(look.x), Math.max(Math.abs(look.y), Math.abs(look.z)));
         if (maxComponent < 0.001) return original;
         // Grim fireworksBox caps at 1.7/axis. Convergence target = S * lookComponent.
         // Use 1.6 margin to stay safely under.
         double safeCap = 1.6 / maxComponent;
         return Math.min(desired, safeCap);
      }
      return original;
   }
}
