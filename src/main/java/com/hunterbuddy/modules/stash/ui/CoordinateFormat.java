package com.hunterbuddy.modules.stash.ui;

import com.hunterbuddy.modules.StashFinder.StashChunk;
import com.hunterbuddy.modules.stash.StashPrivacySettings;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

public final class CoordinateFormat {
    private static final String CENSORED = "*****, *****";
    private static final String CHAT_PREFIX = "StashFinder";

    private CoordinateFormat() {
    }

    public static String shown(StashChunk chunk, StashPrivacySettings privacy) {
        return privacy.streamerMode.get() ? CENSORED : chunk.x + ", " + chunk.z;
    }

    public static void copy(StashChunk chunk, StashPrivacySettings privacy) {
        MeteorClient.mc.keyboard.setClipboard(chunk.x + " " + chunk.z);
        if (privacy.streamerMode.get()) ChatUtils.infoPrefix(CHAT_PREFIX, "Copied coordinates to clipboard.");
        else ChatUtils.infoPrefix(CHAT_PREFIX, "Copied coordinates: " + chunk.x + " " + chunk.z);
    }
}
