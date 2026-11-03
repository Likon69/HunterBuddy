package com.hunterbuddy.modules.flowesp;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

final class FlowRenderer {
    private static final double CHUNK_CENTER_OFFSET = 8.0;
    private static final double BLOCK_CENTER_OFFSET = 0.5;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final FlowOptions options;
    private final FadeStates fades = new FadeStates();
    private final FrameClock clock = new FrameClock();
    private final ColumnPainter gradientPainter;
    private final ColumnPainter solidPainter;
    private RegistryKey<World> lastDimension;

    FlowRenderer(FlowOptions options, FlowPalette palette) {
        this.options = options;
        this.gradientPainter = new GradientPainter(options.general);
        this.solidPainter = new SolidPainter(options.general, palette);
    }

    void reset() {
        fades.reset();
        lastDimension = null;
    }

    void render(Render3DEvent event, FlowCache cache) {
        if (mc.world == null || mc.player == null || mc.world.getRegistryKey() == World.END) return;
        RegistryKey<World> dimension = mc.world.getRegistryKey();
        if (dimension != lastDimension) {
            fades.reset();
            lastDimension = dimension;
        }
        RenderFrame frame = captureFrame();
        ColumnPainter painter = options.general.gradientMode.get() ? gradientPainter : solidPainter;
        painter.begin(event.renderer, frame);
        drawChunks(cache.of(dimension).snapshot(), frame, painter);
    }

    private RenderFrame captureFrame() {
        AnimationOptions animation = options.animation;
        RenderFrame.Pulse pulse = RenderFrame.Pulse.STEADY;
        if (animation.pulseTips.get()) {
            double wave = (Math.sin(2.0 * Math.PI * clock.seconds() * animation.pulseSpeed.get()) + 1.0) / 2.0;
            pulse = new RenderFrame.Pulse(wave, animation.pulseAlpha.get(), animation.pulseSize.get());
        }
        double range = options.general.renderDistance.get();
        return new RenderFrame(mc.player.getEntityPos(), range * range, pulse, fadeStep());
    }

    private double fadeStep() {
        double delta = clock.nextDeltaSeconds();
        double duration = options.animation.fadeDuration.get();
        return !options.animation.fadeLoad.get() || duration == 0.0 ? 1.0 : delta / duration;
    }

    private void drawChunks(List<FlowChunk> chunks, RenderFrame frame, ColumnPainter painter) {
        Set<Long> seen = new HashSet<>();
        double initialFade = options.animation.fadeLoad.get() ? 0.0 : 1.0;
        for (FlowChunk chunk : chunks) {
            long key = chunk.pos().toLong();
            double fade = isInRange(chunk, frame)
                ? fades.fadeIn(key, frame.fadeStep(), initialFade)
                : fades.fadeOut(key, frame.fadeStep());
            seen.add(key);
            if (FadeStates.isVisible(fade)) {
                drawColumns(chunk, fade, frame, painter);
            }
        }
        fades.retainOnly(seen);
    }

    private boolean isInRange(FlowChunk chunk, RenderFrame frame) {
        double dx = chunk.pos().getStartX() + CHUNK_CENTER_OFFSET - frame.viewer().x;
        double dz = chunk.pos().getStartZ() + CHUNK_CENTER_OFFSET - frame.viewer().z;
        return dx * dx + dz * dz <= frame.renderDistanceSquared();
    }

    private void drawColumns(FlowChunk chunk, double fade, RenderFrame frame, ColumnPainter painter) {
        for (FlowColumn column : chunk.columns()) {
            double distanceSquared = distanceSquaredToBottom(chunk, column, frame.viewer());
            if (distanceSquared <= frame.renderDistanceSquared()) {
                painter.paint(chunk, column, fade, frame.distanceRatio(distanceSquared));
            }
        }
    }

    private static double distanceSquaredToBottom(FlowChunk chunk, FlowColumn column, Vec3d viewer) {
        double dx = chunk.pos().getStartX() + column.pos().localX() + BLOCK_CENTER_OFFSET - viewer.x;
        double dy = column.span().bottomY() - viewer.y;
        double dz = chunk.pos().getStartZ() + column.pos().localZ() + BLOCK_CENTER_OFFSET - viewer.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
