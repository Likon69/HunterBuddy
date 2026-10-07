package com.hunterbuddy.modules.sorter;

import com.hunterbuddy.modules.AutoFlyingRegear;
import com.hunterbuddy.modules.SpawnerDetector;
import com.hunterbuddy.modules.logistics.StashMover;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.player.PlayerEntity;
import static meteordevelopment.meteorclient.MeteorClient.mc;

final class SorterSafety {
    static final long PAUSE_LIMIT_NANOS = 120_000_000_000L;
    static final long RESUME_DELAY_NANOS = 5_000_000_000L;

    String waitingReason(boolean ignoreFriends) {
        String driver = driverReason();
        return driver != null ? driver : visiblePlayerReason(ignoreFriends);
    }

    String driverReason() {
        SpawnerDetector spawner = Modules.get().get(SpawnerDetector.class);
        if (spawner != null && spawner.isActive()) {
            return "SpawnerDetector active";
        }
        StashMover mover = Modules.get().get(StashMover.class);
        if (mover != null && mover.isActive()) {
            return "StashMover active";
        }
        AutoFlyingRegear regear = Modules.get().get(AutoFlyingRegear.class);
        if (regear != null && regear.isRunning()) {
            return "AutoFlyingRegear running";
        }
        return null;
    }

    String visiblePlayerReason(boolean ignoreFriends) {
        if (mc.world == null || mc.player == null) {
            return null;
        }
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.getUuid().equals(mc.player.getUuid())) {
                continue;
            }
            if (ignoreFriends && Friends.get().isFriend(player)) {
                continue;
            }
            return "Player visible: " + player.getName().getString();
        }
        return null;
    }
}
