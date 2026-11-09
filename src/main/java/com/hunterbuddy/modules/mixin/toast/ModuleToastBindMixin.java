package com.hunterbuddy.modules.mixin.toast;

import com.hunterbuddy.hud.toast.ModuleToastInput;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Modules.class, remap = false)
public abstract class ModuleToastBindMixin {
    @WrapOperation(method = "onAction", at = {
        @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/systems/modules/Module;toggle()V")
    })
    private static void hunterBuddy$playerToggle(Module module, Operation<Void> original) {
        ModuleToastInput.perform(module, original::call);
    }
}
