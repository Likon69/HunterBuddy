package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.MlepScaffold;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.player.PlayerEntity;

@Mixin(PlayerEntity.class)
public class MlepScaffoldMixin {
   @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
   private void onClipAtLedge(CallbackInfoReturnable<Boolean> cir) {
      if ((Object) this != MeteorClient.mc.player) return;

      MlepScaffold scaffold = Modules.get().get(MlepScaffold.class);
      if (scaffold != null && scaffold.isSafeWalking()) {
         cir.setReturnValue(true);
      }
   }
}