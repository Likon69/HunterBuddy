package com.hunterbuddy.modules.visualrange;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Objects;

public final class PlayerIdentity {
    private PlayerIdentity() {
    }

    public static boolean isSelf(PlayerEntity other) {
        ClientPlayerEntity me = MeteorClient.mc.player;
        if (me == null) return false;
        return other == me
            || Objects.equals(other.getUuid(), me.getUuid())
            || Objects.equals(other.getGameProfile().name(), me.getGameProfile().name());
    }
}
