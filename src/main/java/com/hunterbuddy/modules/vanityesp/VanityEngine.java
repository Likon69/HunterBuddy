package com.hunterbuddy.modules.vanityesp;

import java.util.List;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.chunk.WorldChunk;

public final class VanityEngine {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final List<VanityFeature> features;

    public VanityEngine(VanityOptions options) {
        features = List.of(
            new EntityOutlineFeature(options.mapFrames, options.mapFrameStyle, EntityRules.mapFrames()),
            new BannerFeature(options),
            new EntityOutlineFeature(options.shulkerFrames, options.shulkerFrameStyle, EntityRules.shulkerFrames()),
            new VaultFeature(options),
            new TreasureFeature(options),
            new EntityOutlineFeature(options.endermanHoldingBlocks, options.endermanStyle, EntityRules.endermenHoldingBlocks()),
            new EntityOutlineFeature(options.xpOrbs, options.xpOrbStyle, EntityRules.experienceOrbs()),
            new StackedFeature(options));
    }

    public void activate() {
        features.forEach(VanityFeature::activate);
    }

    public void deactivate() {
        features.forEach(VanityFeature::deactivate);
    }

    public void joined() {
        features.forEach(VanityFeature::joined);
    }

    public void tick() {
        features.forEach(VanityFeature::tick);
    }

    public void chunkData(WorldChunk chunk) {
        features.forEach(feature -> feature.chunkData(chunk));
    }

    public void interact(InteractBlockEvent event) {
        features.forEach(feature -> feature.interact(event));
    }

    public void render(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        features.forEach(feature -> feature.render(event));
    }
}
