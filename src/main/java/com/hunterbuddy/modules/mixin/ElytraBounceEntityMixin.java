package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.ElytraBounce;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.FreeLook;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, priority = 1001)
public abstract class ElytraBounceEntityMixin {
    @Unique
    private static final double MOUSE_SENSITIVITY = 0.15;
    @Unique
    private static final float MAX_PITCH = 90.0F;

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void hunterBuddy$splitLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this != MeteorClient.mc.player) return;
        if (hunterBuddy$freeLookActive()) return;
        ElytraBounce bounce = Modules.get().get(ElytraBounce.class);
        if (bounce == null || !bounce.isFreePitchEnabled()) return;
        bounce.cameraPitch = MathHelper.clamp(
            bounce.cameraPitch + (float) (cursorDeltaY * MOUSE_SENSITIVITY), -MAX_PITCH, MAX_PITCH);
        Entity self = (Entity) (Object) this;
        self.setYaw(self.getYaw() + (float) (cursorDeltaX * MOUSE_SENSITIVITY));
        ci.cancel();
    }

    @Unique
    private static boolean hunterBuddy$freeLookActive() {
        FreeLook freeLook = Modules.get().get(FreeLook.class);
        return freeLook != null && freeLook.isActive();
    }
}
