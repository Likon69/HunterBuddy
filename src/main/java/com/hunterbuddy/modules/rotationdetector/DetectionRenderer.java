package com.hunterbuddy.modules.rotationdetector;

import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class DetectionRenderer {
    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final RotationDetectorOptions options;
    private final DetectionEngine engine;

    public DetectionRenderer(RotationDetectorOptions options, DetectionEngine engine) {
        this.options = options;
        this.engine = engine;
    }

    public void render(Render3DEvent event) {
        if (mc.player == null) return;
        Vec3d viewer = mc.player.getEntityPos();
        double limit = (double) options.renderDistance.get() * options.renderDistance.get();
        for (Set<BlockPos> blocks : engine.cache().chunkBlocks()) {
            for (BlockPos pos : blocks) {
                if (Vec3d.ofCenter(pos).squaredDistanceTo(viewer) <= limit) {
                    drawBox(event, pos);
                }
            }
        }
    }

    private void drawBox(Render3DEvent event, BlockPos pos) {
        event.renderer.box(pos, options.sideColor.get(), options.lineColor.get(), options.shapeMode.get(), 0);
    }
}
