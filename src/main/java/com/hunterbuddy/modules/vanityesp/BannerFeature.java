package com.hunterbuddy.modules.vanityesp;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.chunk.WorldChunk;

final class BannerFeature implements VanityFeature {
    private static final int CHUNK_RADIUS = 8;
    private static final int CHUNK_SHIFT = 4;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final VanityOptions options;

    BannerFeature(VanityOptions options) {
        this.options = options;
    }

    @Override
    public void render(Render3DEvent event) {
        if (!options.banners.get() || !options.bannerStyle.drawsShapes() || mc.player == null || mc.world == null) return;
        int centreX = mc.player.getBlockX() >> CHUNK_SHIFT;
        int centreZ = mc.player.getBlockZ() >> CHUNK_SHIFT;
        for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
            for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                drawChunk(event, mc.world.getChunkManager().getWorldChunk(centreX + dx, centreZ + dz));
            }
        }
    }

    private void drawChunk(Render3DEvent event, WorldChunk chunk) {
        if (chunk == null) return;
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof BannerBlockEntity) {
                Outline.block(event, blockEntity.getPos(), options.bannerStyle);
            }
        }
    }
}
