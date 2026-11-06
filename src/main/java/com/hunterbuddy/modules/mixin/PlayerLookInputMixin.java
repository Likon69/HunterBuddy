package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.WaypointFollower;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class PlayerLookInputMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"))
    private void hunterBuddy$noteLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this != MeteorClient.mc.player) return;
        WaypointFollower follower = Modules.get().get(WaypointFollower.class);
        if (follower != null) follower.noteManualLook(cursorDeltaX, cursorDeltaY);
    }
}
