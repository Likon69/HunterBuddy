package com.hunterbuddy.modules.flowesp;

import com.hunterbuddy.modules.FlowESP.GradientSource;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.utils.render.color.Color;

final class GradientPainter implements ColumnPainter {
    private static final double MIN_AGE_ALPHA = 0.02;
    private static final double MIN_DISTANCE_ALPHA = 0.05;
    private static final double MAX_SPREAD = 0.15;
    private static final double SPREAD_PER_BLOCK = 0.005;
    private static final double FLOW_WEIGHT = 0.6;
    private static final double AGE_WEIGHT = 0.4;
    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final int MAX_CHANNEL = 255;

    private final GeneralOptions general;
    private final Color top = new Color();
    private final Color bottom = new Color();
    private Renderer3D renderer;
    private RenderFrame frame;

    GradientPainter(GeneralOptions general) {
        this.general = general;
    }

    @Override
    public void begin(Renderer3D renderer, RenderFrame frame) {
        this.renderer = renderer;
        this.frame = frame;
    }

    @Override
    public void paint(FlowChunk chunk, FlowColumn column, double fade, double distanceRatio) {
        double maturity = maturity(chunk, column);
        double alpha = fade * Math.max(MIN_AGE_ALPHA, 1.0 - maturity * general.alphaFade.get())
            * Math.max(MIN_DISTANCE_ALPHA, 1.0 - distanceRatio * general.distanceFade.get()) * frame.alphaFactor(column);
        double spread = Math.min(MAX_SPREAD, column.span().height() * SPREAD_PER_BLOCK);
        Stops stops = stopsFor(column);
        blend(top, stops, Math.max(0.0, maturity - spread), alpha);
        blend(bottom, stops, Math.min(1.0, maturity + spread), alpha);
        drawBox(ColumnBox.of(chunk, column, frame.expansion(column)));
    }

    private double maturity(FlowChunk chunk, FlowColumn column) {
        double flowFactor = Math.min(1.0, (double) column.flowLength() / general.maxFlowLength.get());
        double age = System.currentTimeMillis() - chunk.firstSeenMs();
        double ageFactor = Math.min(1.0, age / (general.maxAgeSeconds.get() * MILLIS_PER_SECOND));
        return switch (general.gradientSource.get()) {
            case FlowLength -> flowFactor;
            case ChunkAge -> ageFactor;
            case Both -> FLOW_WEIGHT * flowFactor + AGE_WEIGHT * ageFactor;
        };
    }

    private record Stops(Color fresh, Color mature) {
    }

    private Stops stopsFor(FlowColumn column) {
        if (column.flags().lava()) return new Stops(general.lavaFresh.get(), general.lavaMature.get());
        return new Stops(general.waterFresh.get(), general.waterMature.get());
    }

    private void blend(Color target, Stops stops, double t, double alpha) {
        Color fresh = stops.fresh();
        Color mature = stops.mature();
        double blendedAlpha = (fresh.a + (mature.a - fresh.a) * t) * alpha;
        target.set((int) (fresh.r + (mature.r - fresh.r) * t), (int) (fresh.g + (mature.g - fresh.g) * t),
            (int) (fresh.b + (mature.b - fresh.b) * t), (int) Math.max(0, Math.min(MAX_CHANNEL, blendedAlpha)));
    }

    private void drawBox(ColumnBox box) {
        double[][] corners = corners(box);
        for (int index = 0; index < corners.length; index++) {
            double[] from = corners[index];
            double[] to = corners[(index + 1) % corners.length];
            drawSide(from, to, box);
            drawEdges(from, to, box);
        }
        renderer.quadHorizontal(box.minX(), box.maxY(), box.minZ(), box.maxX(), box.maxZ(), top);
        renderer.quadHorizontal(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxZ(), bottom);
    }

    private static double[][] corners(ColumnBox box) {
        return new double[][]{{box.minX(), box.minZ()}, {box.maxX(), box.minZ()}, {box.maxX(), box.maxZ()}, {box.minX(), box.maxZ()}};
    }

    private void drawSide(double[] from, double[] to, ColumnBox box) {
        renderer.quad(from[0], box.minY(), from[1], from[0], box.maxY(), from[1], to[0], box.maxY(), to[1], to[0], box.minY(), to[1],
            bottom, top, top, bottom);
    }

    private void drawEdges(double[] from, double[] to, ColumnBox box) {
        renderer.line(from[0], box.minY(), from[1], to[0], box.minY(), to[1], bottom);
        renderer.line(from[0], box.maxY(), from[1], to[0], box.maxY(), to[1], top);
        renderer.line(from[0], box.minY(), from[1], from[0], box.maxY(), from[1], bottom, top);
    }
}
