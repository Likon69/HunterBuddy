package com.hunterbuddy.modules.visualrange;

import com.hunterbuddy.util.HuntFeed;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class PvpAnnouncer {
    private static final String UNKNOWN_PLAYER = "Unknown";
    private static final String LABEL_SEPARATOR = " · ";

    private final PvpSettings pvp;
    private final PlayerRangeSettings players;
    private final DiscordActivitySettings discordActivity;
    private final AlertSink alerts;

    public PvpAnnouncer(
        PvpSettings pvp, PlayerRangeSettings players, DiscordActivitySettings discordActivity, AlertSink alerts
    ) {
        this.pvp = pvp;
        this.players = players;
        this.discordActivity = discordActivity;
        this.alerts = alerts;
    }

    public void announce(PvpEvent event, Vec3d point) {
        String who = nearestPlayerName(point);
        BlockPos position = BlockPos.ofFloored(point);
        String coordinates = position.getX() + " " + position.getY() + " " + position.getZ();
        String label = UNKNOWN_PLAYER.equals(who) ? event.feedLabel() : event.feedLabel() + LABEL_SEPARATOR + who;
        alerts.feed(HuntFeed.Type.DANGER, label, position);
        alerts.chat(event.chatText(who, coordinates));
        alerts.ding();
        alerts.discord(discordActivity.pvp, event.webhookTitle(),
            event.webhookText(who, coordinates, DimensionNames.current()));
    }

    private String nearestPlayerName(Vec3d point) {
        PlayerEntity nearest = null;
        double nearestDistance = pvp.pvpRange.get();
        for (PlayerEntity player : MeteorClient.mc.world.getPlayers()) {
            if (!isCandidate(player)) continue;
            double distance = player.getEntityPos().distanceTo(point);
            if (distance <= nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest == null ? UNKNOWN_PLAYER : nearest.getGameProfile().name();
    }

    private boolean isCandidate(PlayerEntity player) {
        if (pvp.ignoreSelf.get() && PlayerIdentity.isSelf(player)) return false;
        return !(players.ignoreFriends.get() && Friends.get().isFriend(player));
    }
}
