package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.events.ServerDisconnectEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.DisconnectionInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the end of a connection into an event, whatever ended it.
 *
 * <p>One hook covers every case — server kick, timeout, and our own modules,
 * which reach this through {@code onDisconnect}. That is why nothing here talks
 * to {@code AutoLogPlus}: it already writes its own reason into the text it
 * sends, so "why" arrives on its own.
 *
 * <p>The client stays on the disconnect screen afterwards rather than closing,
 * so a listener has time to finish a network call. A JVM crash or a killed
 * process is the one case nothing can cover.
 */
@Mixin(ClientCommonNetworkHandler.class)
public class ServerDisconnectMixin {
    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void hb$onDisconnected(DisconnectionInfo info, CallbackInfo ci) {
        MeteorClient.EVENT_BUS.post(new ServerDisconnectEvent(info.reason()));
    }
}
