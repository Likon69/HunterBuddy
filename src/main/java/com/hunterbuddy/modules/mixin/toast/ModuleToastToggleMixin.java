package com.hunterbuddy.modules.mixin.toast;

import com.hunterbuddy.hud.toast.ModuleToastFeed;
import com.hunterbuddy.hud.toast.ModuleToastInput;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Module.class, remap = false)
public abstract class ModuleToastToggleMixin {
    @WrapMethod(method = "toggle")
    private void hunterBuddy$toggleScope(Operation<Void> original) {
        ModuleToastInput input = ModuleToastInput.current();
        int previous = input.enter(this);
        try {
            original.call();
        } finally {
            input.leave(previous);
        }
    }

    @Inject(method = "toggle", at = {
        @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/systems/modules/Modules;addActive(Lmeteordevelopment/meteorclient/systems/modules/Module;)V"),
        @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/systems/modules/Modules;removeActive(Lmeteordevelopment/meteorclient/systems/modules/Module;)V")
    })
    private void hunterBuddy$recordPlayerTransition(CallbackInfo info) {
        ModuleToastFeed.INSTANCE.record((Module) (Object) this);
    }
}
