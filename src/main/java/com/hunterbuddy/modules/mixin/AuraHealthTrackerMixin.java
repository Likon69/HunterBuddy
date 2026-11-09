package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.modules.aura.AuraVitals;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class AuraHealthTrackerMixin {
    @Inject(method = "onEntityTrackerUpdate", at = @At("TAIL"))
    private void hunterBuddy$health(EntityTrackerUpdateS2CPacket packet, CallbackInfo info) {
        AuraVitals.tracked(packet.id());
    }

    @Inject(method = "onEntityAnimation", at = @At("TAIL"))
    private void hunterBuddy$critical(EntityAnimationS2CPacket packet, CallbackInfo info) {
        if (packet.getAnimationId() == EntityAnimationS2CPacket.CRIT) AuraVitals.critical(packet.getEntityId());
    }
}
