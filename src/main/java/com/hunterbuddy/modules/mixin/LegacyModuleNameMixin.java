package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.util.LegacyNames;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Modules.class, remap = false)
public abstract class LegacyModuleNameMixin {
    @Inject(method = "get(Ljava/lang/String;)Lmeteordevelopment/meteorclient/systems/modules/Module;", at = @At("RETURN"), cancellable = true)
    private void hunterBuddy$legacyName(String name, CallbackInfoReturnable<Module> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }

        String renamed = LegacyNames.module(name);
        if (renamed != null) {
            cir.setReturnValue(((Modules) (Object) this).get(renamed));
        }
    }
}
