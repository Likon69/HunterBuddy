package com.hunterbuddy.modules.visualrange;

import com.hunterbuddy.HunterBuddyAddon;
import com.hunterbuddy.modules.regear.util.Utils;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.MinecraftClient;

public final class DiscordReporter {
    private static final String UNKNOWN_SENDER = "Unknown";

    private final DiscordSettings settings;

    public DiscordReporter(DiscordSettings settings) {
        this.settings = settings;
    }

    public boolean ready(Setting<Boolean> category) {
        return settings.enabled.get() && category.get() && !settings.webhookUrl.get().isBlank();
    }

    public void send(String title, String message) {
        String url = settings.webhookUrl.get();
        String ping = pingTarget();
        String sender = senderName();
        HunterBuddyAddon.LOG.info("[HB] webhook queued: {}", title);
        MeteorExecutor.execute(() -> {
            Utils.sendWebhook(url, title, message, ping, sender);
            HunterBuddyAddon.LOG.info("[HB] webhook sent: {}", title);
        });
    }

    private String pingTarget() {
        String id = settings.pingId.get();
        return id.isBlank() ? null : id;
    }

    private static String senderName() {
        MinecraftClient mc = MeteorClient.mc;
        if (mc.player != null) return mc.player.getGameProfile().name();
        if (mc.getSession() != null) return mc.getSession().getUsername();
        return UNKNOWN_SENDER;
    }
}
