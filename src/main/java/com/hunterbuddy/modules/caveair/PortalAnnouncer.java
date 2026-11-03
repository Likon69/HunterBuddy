package com.hunterbuddy.modules.caveair;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

final class PortalAnnouncer {
    private static final float SOUND_PITCH = 1.5f;
    private static final float SOUND_VOLUME = 1.0f;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final CaveAirOptions options;
    private final Set<BlockPos> announced = ConcurrentHashMap.newKeySet();

    PortalAnnouncer(CaveAirOptions options) {
        this.options = options;
    }

    void forget() {
        announced.clear();
    }

    void announce(ChunkResult result) {
        for (CellBox portal : result.portals()) {
            if (announced.add(portal.minCorner())) {
                mc.execute(() -> notifyFound(portal));
            }
        }
        if (options.debug.get()) {
            mc.execute(() -> summarize(result));
        }
    }

    private void notifyFound(CellBox portal) {
        if (options.portalSound.get()) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SOUND_PITCH, SOUND_VOLUME));
        }
        if (options.debug.get()) {
            ChatUtils.info("Portal detected at %d, %d, %d", portal.minX(), portal.minY(), portal.minZ());
        }
    }

    private void summarize(ChunkResult result) {
        List<CellBox> portals = result.portals();
        ChatUtils.info("Chunk %d, %d: %d portals, %d cave air boxes, %d enclosed air blocks", result.chunkX(), result.chunkZ(),
            portals.size(), result.caveAirBoxes().size(), result.enclosedAir().size());
    }
}
