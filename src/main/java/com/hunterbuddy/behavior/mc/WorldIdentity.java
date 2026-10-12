package com.hunterbuddy.behavior.mc;

import java.util.Objects;
import net.minecraft.client.MinecraftClient;

public record WorldIdentity(String dimensionKey, int worldId, int playerId) {
    public WorldIdentity {
        Objects.requireNonNull(dimensionKey);
    }

    public static WorldIdentity capture(MinecraftClient client) {
        return new WorldIdentity(client.world == null ? "none" : client.world.getRegistryKey().getValue().toString(),
            client.world == null ? 0 : System.identityHashCode(client.world), client.player == null ? -1 : client.player.getId());
    }
}
