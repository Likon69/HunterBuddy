package com.hunterbuddy.modules.mixin.toast;

import com.hunterbuddy.gui.theme.widgets.HbModule;
import com.hunterbuddy.gui.theme.widgets.HbModuleSettings;
import com.hunterbuddy.hud.toast.ModuleToastInput;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import meteordevelopment.meteorclient.gui.screens.ModuleScreen;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.WMeteorModule;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = {WMeteorModule.class, ModuleScreen.class, HbModule.class, HbModuleSettings.class}, remap = false)
public abstract class ModuleToastGuiMixin {
    @WrapOperation(method = "*", at = {
        @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/systems/modules/Module;toggle()V")
    })
    private static void hunterBuddy$playerToggle(Module module, Operation<Void> original) {
        ModuleToastInput.perform(module, original::call);
    }
}
