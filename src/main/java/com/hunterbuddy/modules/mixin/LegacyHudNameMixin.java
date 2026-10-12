package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.util.LegacyNames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import meteordevelopment.meteorclient.systems.hud.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Hud.class, remap = false)
public abstract class LegacyHudNameMixin {
    @WrapOperation(
        method = "fromTag",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private Object hunterBuddy$legacyHudName(Map<?, ?> map, Object key, Operation<Object> original) {
        Object result = original.call(map, key);
        if (result != null || !(key instanceof String name)) {
            return result;
        }

        String renamed = LegacyNames.hud(name);
        return renamed != null ? original.call(map, renamed) : result;
    }
}
