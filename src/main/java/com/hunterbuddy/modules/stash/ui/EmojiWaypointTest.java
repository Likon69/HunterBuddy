package com.hunterbuddy.modules.stash.ui;

import com.hunterbuddy.modules.regear.util.MapUtil;
import com.hunterbuddy.modules.regear.util.XaeroWaypointManager;
import com.hunterbuddy.modules.stash.StashDimensions;
import com.hunterbuddy.modules.stash.StashType;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;

public final class EmojiWaypointTest {
    private static final String CHAT_PREFIX = "StashFinder";
    private static final int SPACING = 16;

    private EmojiWaypointTest() {
    }

    public static void run() {
        ClientPlayerEntity player = MeteorClient.mc.player;
        if (player == null) {
            ChatUtils.errorPrefix(CHAT_PREFIX, "Player not found");
            return;
        }
        if (!XaeroWaypointManager.isAvailable()) {
            ChatUtils.errorPrefix(CHAT_PREFIX, "Xaero's Minimap not available");
            return;
        }
        int created = 0;
        for (StashType type : StashType.values()) {
            if (!type.showsInDetail()) continue;
            place(player.getBlockPos().add(created * SPACING, 0, 0), type);
            created++;
        }
        ChatUtils.infoPrefix(CHAT_PREFIX,
            "Created %d test waypoints at your location. Check which emojis render correctly!", created);
    }

    private static void place(BlockPos position, StashType type) {
        XaeroWaypointManager.addWaypoint(
            CHAT_PREFIX,
            position,
            type.symbol() + " " + type.singular(),
            type.symbol(),
            MapUtil.WpColor.Gold,
            true,
            StashDimensions.current());
    }
}
