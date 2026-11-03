package com.hunterbuddy.modules.stash;

import com.hunterbuddy.modules.StashFinder.StashChunk;
import com.hunterbuddy.util.HuntFeed;
import com.hunterbuddy.util.SessionStats;
import net.minecraft.util.math.BlockPos;

public final class StashAnnouncer {
    private final StashGeneralSettings general;
    private final StashWaypoints waypoints;
    private final StashNotifier notifier;
    private final StashDiscord discord;

    public StashAnnouncer(
        StashGeneralSettings general, StashWaypoints waypoints, StashNotifier notifier, StashDiscord discord
    ) {
        this.general = general;
        this.waypoints = waypoints;
        this.notifier = notifier;
        this.discord = discord;
    }

    public void announce(StashChunk chunk) {
        waypoints.update(chunk);
        SessionStats.get().onStashFound();
        HuntFeed.get().publish(
            HuntFeed.Type.STASH, "Stash · " + chunk.getTotal() + " containers", new BlockPos(chunk.x, 0, chunk.z));
        if (general.notifications.get()) notifier.notify(chunk);
        discord.send(chunk);
    }
}
