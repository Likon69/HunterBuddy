package com.hunterbuddy.modules.mixin.rocketboost;

import com.hunterbuddy.modules.RocketBoost;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerMoveC2SPacket.class)
public class PlayerMoveC2SPacketLookBypassMixin {
   @ModifyReturnValue(method = "getYaw()F", at = @At("RETURN"))
   public float modifyYaw(float original) {
      if (!RocketBoost.lookBypassActive()) return original;
      return original + RocketBoost.getLookOffsetYaw();
   }

   @ModifyReturnValue(method = "getPitch()F", at = @At("RETURN"))
   public float modifyPitch(float original) {
      if (!RocketBoost.lookBypassActive()) return original;
      return original + RocketBoost.getLookOffsetPitch();
   }
}
