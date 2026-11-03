package com.hunterbuddy.modules.stash;

import com.hunterbuddy.HunterBuddyAddon;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.math.ChunkPos;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;
import xaeroplus.util.ChunkUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class OldChunkGate {
    private static final String CHAT_PREFIX = "StashFinder";
    private static final String MODULE_OFF = "XaeroPlus's Old Chunks module is off";
    private static final String MODULE_UNREACHABLE = "XaeroPlus's Old Chunks module could not be reached";

    private final StashFilterSettings settings;
    private final Set<ChunkPos> pending = new HashSet<>();
    private boolean warned;

    public OldChunkGate(StashFilterSettings settings) {
        this.settings = settings;
    }

    public void reset() {
        pending.clear();
        warned = false;
    }

    public boolean defersScans() {
        return settings.oldChunksOnly.get();
    }

    public void defer(ChunkPos position) {
        pending.add(position);
    }

    public List<ChunkPos> drainAccepted() {
        List<ChunkPos> drained = new ArrayList<>(pending);
        pending.clear();
        drained.removeIf(position -> !isOld(position.x, position.z));
        return drained;
    }

    public boolean isOld(int chunkX, int chunkZ) {
        try {
            OldChunks module = ModuleManager.getModule(OldChunks.class);
            if (module == null) return warnUnavailable(MODULE_UNREACHABLE, null);
            if (!module.isEnabled()) return warnUnavailable(MODULE_OFF, null);
            return module.isOldChunk(chunkX, chunkZ, ChunkUtils.getActualDimension());
        } catch (Throwable failure) {
            return warnUnavailable(MODULE_UNREACHABLE, failure);
        }
    }

    private boolean warnUnavailable(String reason, Throwable failure) {
        if (warned) return false;
        warned = true;
        ChatUtils.warningPrefix(CHAT_PREFIX,
            "old-chunks-only is on but " + reason + " - nothing will be reported until it is available");
        if (failure instanceof Exception) HunterBuddyAddon.LOG.error("StashFinder old chunk check failed", failure);
        return false;
    }
}
