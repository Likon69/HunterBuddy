package com.hunterbuddy.modules.scaffold;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

final class PlacedBlocks {
    private final List<BlockPos> positions = new ArrayList<>();

    void add(BlockPos position) {
        positions.add(position.toImmutable());
    }

    void clear() {
        positions.clear();
    }

    void render(Render3DEvent event, ScaffoldRenderSettings settings) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!settings.enabled.get() || positions.isEmpty() || mc.world == null) return;
        positions.removeIf(position -> mc.world.getBlockState(position).isReplaceable());
        for (BlockPos position : positions) {
            event.renderer.box(position, settings.sideColor.get(), settings.lineColor.get(), settings.shapeMode.get(), 0);
        }
    }
}
