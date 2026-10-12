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
    private ElytraBounce hunterBuddy$bounce;

    @Inject(at = @At("RETURN"), method = "isPressed", cancellable = true)
    public void hunterBuddy$isPressed(CallbackInfoReturnable<Boolean> cir) {
        Modules modules = Modules.get();
        if (modules == null) return;
        if (this.hunterBuddy$bounce == null) this.hunterBuddy$bounce = modules.get(ElytraBounce.class);
        ElytraBounce bounce = this.hunterBuddy$bounce;
        if (bounce == null || !bounce.isActive() || !bounce.enabled()) return;
        Boolean held = bounce.keyOverride(this.id);
        if (held != null) cir.setReturnValue(held);
    }
}
