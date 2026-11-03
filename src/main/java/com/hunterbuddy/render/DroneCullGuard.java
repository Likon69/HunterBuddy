package com.hunterbuddy.render;

import net.minecraft.client.MinecraftClient;

public final class DroneCullGuard {
    private static final double RELEASE_SECONDS = 1.0;

    private boolean saved;
    private boolean original;
    private double sinceInside = RELEASE_SECONDS;

    public void update(boolean insideBlock, double dt) {
        if (insideBlock) {
            sinceInside = 0.0;
            disable();
            return;
        }

        sinceInside += dt;
        if (sinceInside >= RELEASE_SECONDS) restore();
    }

    public void restore() {
        sinceInside = RELEASE_SECONDS;
        if (!saved) return;

        saved = false;
        MinecraftClient client = MinecraftClient.getInstance();
        client.chunkCullingEnabled = original;
        if (client.worldRenderer != null) client.worldRenderer.scheduleTerrainUpdate();
    }

    private void disable() {
        if (saved) return;

        MinecraftClient client = MinecraftClient.getInstance();
        original = client.chunkCullingEnabled;
        saved = true;
        client.chunkCullingEnabled = false;
        if (client.worldRenderer != null) client.worldRenderer.scheduleTerrainUpdate();
    }
}
