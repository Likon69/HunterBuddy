package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.ElytraBounce;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    @Final
    @Shadow
    private String id;
    @Unique
    ElytraBounce efly = null;

    @Inject(at = @At("RETURN"), method = "isPressed", cancellable = true)
    public void hunterBuddy$isPressed(CallbackInfoReturnable<Boolean> cir) {
        if (Modules.get() != null) {
            this.efly = this.efly == null ? (ElytraBounce) Modules.get().get(ElytraBounce.class) : this.efly;
            if (this.efly != null && this.efly.isActive() && this.efly.enabled() && this.id.equals("key.forward")) {
                cir.setReturnValue(true);
            }
        }
    }
}
