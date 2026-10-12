package com.hunterbuddy.behavior.resource;

import java.util.Objects;
import net.minecraft.client.MinecraftClient;

public final class LookOutput {
    private final MinecraftClient client;

    public LookOutput(MinecraftClient client) {
        this.client = Objects.requireNonNull(client);
    }

    public void apply(LookIntent intent) {
        if (intent == null || client.player == null || client.world == null) return;
        client.player.setYaw(intent.yaw());
        client.player.setPitch(intent.pitch());
    }
}
