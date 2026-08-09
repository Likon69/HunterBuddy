package com.hunterbuddy.modules.mixin;

import com.hunterbuddy.events.PlayerDeathEvent;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns the server's death announcement into an event.
 *
 * <p>Read from the packet rather than from the player's health. Watching
 * {@code getHealth() <= 0} looks simpler but is unreliable: health dips through
 * transient states, respawn resets it, and regeneration can sit on the boundary,
 * so the condition either misses a death or fires several times for one. The
 * packet arrives exactly once, at the moment it happens, and already carries the
 * fully formatted reason.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class PlayerDeathMixin {
    @Inject(method = "onDeathMessage", at = @At("HEAD"))
    private void hb$onDeathMessage(DeathMessageS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // The handler runs twice for one packet. Its first statement is
        // NetworkThreadUtils.forceMainThread, which — off the main thread — queues
        // the packet again and throws, so the method is re-entered on the client
        // thread. A HEAD injection therefore fires once on netty and once on main:
        // two webhooks per death, the first of them reading the player's position
        // from the wrong thread. Dropping the netty pass leaves exactly one.
        if (!client.isOnThread()) return;

        var player = client.player;

        // The packet is also sent for other players' deaths on some servers.
        if (player == null || packet.playerId() != player.getId()) return;

        MeteorClient.EVENT_BUS.post(new PlayerDeathEvent(packet.message()));
    }
}
