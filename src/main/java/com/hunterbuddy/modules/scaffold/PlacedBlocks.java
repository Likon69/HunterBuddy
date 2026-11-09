package com.hunterbuddy.modules.scaffold;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

final class PlacedBlocks {
    private final Map<BlockPos, Long> positions = new LinkedHashMap<>();

    void add(BlockPos position) {
        positions.put(position.toImmutable(), System.currentTimeMillis());
    }

    void clear() {
        positions.clear();
    }

    void render(Render3DEvent event, ScaffoldRenderSettings settings) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!settings.enabled.get() || positions.isEmpty() || mc.world == null) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, Long>> iterator = positions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            if (mc.world.getBlockState(entry.getKey()).isReplaceable()) {
                if (now - entry.getValue() >= 150) iterator.remove();
                continue;
            }
            event.renderer.box(entry.getKey(), settings.sideColor.get(), settings.lineColor.get(), settings.shapeMode.get(), 0);
        }
    }
}
