package com.hunterbuddy.modules.mixin.toast;

import com.hunterbuddy.hud.toast.ModuleToastInput;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.commands.commands.ToggleCommand;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ToggleCommand.class, remap = false)
public abstract class ModuleToastCommandMixin {
    @WrapOperation(method = "*", at = {
        @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/systems/modules/Module;toggle()V"),
        @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/systems/modules/Module;enable()V"),
        @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/systems/modules/Module;disable()V")
    })
    private static void hunterBuddy$playerToggle(Module module, Operation<Void> original) {
        ModuleToastInput.perform(module, original::call);
    }

    @ModifyArg(method = "*", at = @At(value = "INVOKE",
        target = "Ljava/util/ArrayList;forEach(Ljava/util/function/Consumer;)V"), index = 0)
    private static Consumer<Module> hunterBuddy$commandAll(Consumer<Module> action) {
        return module -> ModuleToastInput.perform(module, action);
    }
}
