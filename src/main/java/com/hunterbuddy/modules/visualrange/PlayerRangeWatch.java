package com.hunterbuddy.modules.visualrange;

import com.hunterbuddy.util.HuntFeed;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerRangeWatch {
    private static final String LABEL_SEPARATOR = " · ";

    private final PlayerRangeSettings settings;
    private final DiscordSettings discord;
    private final AlertSink alerts;
    private final Set<UUID> tracked = new HashSet<>();
    private final Map<UUID, String> names = new HashMap<>();

    public PlayerRangeWatch(PlayerRangeSettings settings, DiscordSettings discord, AlertSink alerts) {
        this.settings = settings;
        this.discord = discord;
        this.alerts = alerts;
    }

    public void clear() {
        tracked.clear();
        names.clear();
    }

    public void tick() {
        Map<UUID, PlayerEntity> present = presentPlayers();
        for (Map.Entry<UUID, PlayerEntity> entry : present.entrySet()) {
            if (!tracked.contains(entry.getKey()) && isReportable(entry.getValue().getGameProfile().name())) {
                announceEntry(entry.getValue());
            }
        }
        for (UUID id : tracked) {
            if (!present.containsKey(id)) announceDeparture(id);
        }
        tracked.clear();
        tracked.addAll(present.keySet());
    }

    private Map<UUID, PlayerEntity> presentPlayers() {
        Map<UUID, PlayerEntity> present = new LinkedHashMap<>();
        for (PlayerEntity player : MeteorClient.mc.world.getPlayers()) {
            if (PlayerIdentity.isSelf(player)) continue;
            present.put(player.getUuid(), player);
            names.put(player.getUuid(), player.getGameProfile().name());
        }
        return present;
    }

    private boolean isReportable(String name) {
        return !settings.ignoreFriends.get() || Friends.get().get(name) == null;
    }

    private void announceEntry(PlayerEntity player) {
        String name = player.getGameProfile().name();
        BlockPos position = player.getBlockPos();
        boolean withGear = settings.playerEquipment.get();
        String gear = withGear ? PlayerEquipment.describe(player) : "";
        String tags = withGear ? PlayerEquipment.tags(player) : "";
        alerts.feed(HuntFeed.Type.PLAYER_ENTER, tags.isEmpty() ? name : name + LABEL_SEPARATOR + tags, position);
        if (!settings.playerEnter.get()) return;
        alerts.chat(entryChat(name, position, gear));
        alerts.ding();
        alerts.discord(discord.players, "Player Entered Visual Range", entryWebhook(name, position, gear));
    }

    private String entryChat(String name, BlockPos position, String gear) {
        StringBuilder text = new StringBuilder("§a").append(name).append(" §7entered visual range");
        if (settings.playerCoords.get()) text.append(" at §f").append(coordinates(position));
        if (!gear.isEmpty()) text.append("\n §8└ §7Gear: §f").append(gear);
        return text.toString();
    }

    private String entryWebhook(String name, BlockPos position, String gear) {
        StringBuilder text = new StringBuilder("**").append(name).append("** entered visual range");
        if (settings.playerCoords.get()) text.append("\nCoords: ").append(coordinates(position));
        text.append("\nDimension: ").append(DimensionNames.current());
        if (!gear.isEmpty()) text.append("\nGear: ").append(gear);
        return text.toString();
    }

    private void announceDeparture(UUID id) {
        String name = departedName(id);
        if (!isReportable(name)) return;
        alerts.feed(HuntFeed.Type.PLAYER_LEAVE, name, null);
        if (!settings.playerLeave.get()) return;
        alerts.chat("§c" + name + " §7left visual range.");
        alerts.discord(discord.players, "Player Left Visual Range",
            "**" + name + "** left visual range\nDimension: " + DimensionNames.current());
    }

    private String departedName(UUID id) {
        PlayerListEntry entry = MeteorClient.mc.getNetworkHandler().getPlayerListEntry(id);
        if (entry != null) return entry.getProfile().name();
        return names.getOrDefault(id, id.toString());
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + " " + position.getY() + " " + position.getZ();
    }
}
