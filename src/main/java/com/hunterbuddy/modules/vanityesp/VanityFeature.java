package com.hunterbuddy.modules.vanityesp;

import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.world.chunk.WorldChunk;

interface VanityFeature {
    default void activate() {
    }

    default void deactivate() {
    }

    default void tick() {
    }

    default void chunkData(WorldChunk chunk) {
    }

    default void interact(InteractBlockEvent event) {
    }

    default void joined() {
    }

    default void render(Render3DEvent event) {
    }
}
