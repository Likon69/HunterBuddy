package com.hunterbuddy.modules.stash;

import com.hunterbuddy.modules.StashFinder.StashChunk;
import meteordevelopment.meteorclient.MeteorClient;

public final class TracerMarker {
    private final TracerBoard board;
    private final StashRenderSettings settings;

    public TracerMarker(TracerBoard board, StashRenderSettings settings) {
        this.board = board;
        this.settings = settings;
    }

    public void mark(StashChunk chunk) {
        if (settings.renderTracer.get() && MeteorClient.mc.player != null) {
            board.markAtEyeHeight(chunk, MeteorClient.mc.player);
        }
    }
}
