package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.mixin.accessors.InputMovementAccessor;
import com.hunterbuddy.util.QuietClicks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class QuietClicksMixin {
   @Inject(
      method = "tickMovement",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V", shift = At.Shift.AFTER)
   )
   private void hunterBuddy$settle(CallbackInfo ci) {
      if (!QuietClicks.takeSettle()) return;

      ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
      self.input.playerInput = PlayerInput.DEFAULT;
      ((InputMovementAccessor) self.input).hunterBuddy$setMovementVector(Vec2f.ZERO);
      self.setSprinting(false);
   }
}
