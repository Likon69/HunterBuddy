package com.hunterbuddy.hud.cards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;

public final class PlayerTracker {
    private static final long GHOST_MS = 10_000L;

    private final Map<UUID, PlayerTrack> tracks = new HashMap<>();

    public void clear() {
        tracks.clear();
    }

    public void update(boolean includeFriends, long now) {
        MinecraftClient mc = MeteorClient.mc;
        if (mc.player == null || mc.world == null) {
            tracks.clear();
            return;
        }
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.getUuid().equals(mc.player.getUuid())) continue;
            if (!includeFriends && Friends.get().isFriend(player)) continue;
            tracks.computeIfAbsent(player.getUuid(), uuid -> new PlayerTrack())
                .observe(player, skin(mc, player.getUuid()), mc.player.distanceTo(player), now);
        }
        tracks.values().removeIf(track -> now - track.lastSeenAt() > GHOST_MS);
    }

    public List<PlayerTrack> nearest(int limit, long now) {
        List<PlayerTrack> sorted = new ArrayList<>(tracks.values());
        sorted.sort((a, b) -> {
            boolean ghostA = !a.present(now);
            boolean ghostB = !b.present(now);
            if (ghostA != ghostB) return ghostA ? 1 : -1;
            return Double.compare(a.distance(), b.distance());
        });
        return sorted.size() > limit ? sorted.subList(0, limit) : sorted;
    }

    private static SkinTextures skin(MinecraftClient mc, UUID uuid) {
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        PlayerListEntry entry = handler == null ? null : handler.getPlayerListEntry(uuid);
        return entry == null ? DefaultSkinHelper.getSkinTextures(uuid) : entry.getSkinTextures();
    }
}
