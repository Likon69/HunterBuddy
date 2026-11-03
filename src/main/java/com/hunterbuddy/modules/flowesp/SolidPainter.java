package com.hunterbuddy.modules.flowesp;

import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;

final class SolidPainter implements ColumnPainter {
    private static final double MIN_AGE_ALPHA = 0.02;
    private static final double MIN_DISTANCE_ALPHA = 0.05;

    private final GeneralOptions general;
    private final FlowPalette palette;
    private final Color scratch = new Color();
    private Renderer3D renderer;
    private RenderFrame frame;

    SolidPainter(GeneralOptions general, FlowPalette palette) {
        this.general = general;
        this.palette = palette;
    }

    @Override
    public void begin(Renderer3D renderer, RenderFrame frame) {
        this.renderer = renderer;
        this.frame = frame;
    }

    @Override
    public void paint(FlowChunk chunk, FlowColumn column, double fade, double distanceRatio) {
        double flowFactor = Math.min(1.0, (double) column.flowLength() / general.maxFlowLength.get());
        double multiplier = fade * Math.max(MIN_AGE_ALPHA, 1.0 - flowFactor * general.alphaFade.get())
            * Math.max(MIN_DISTANCE_ALPHA, 1.0 - distanceRatio * general.distanceFade.get()) * frame.alphaFactor(column);
        Color base = palette.solidColor(chunk, column);
        scratch.set(base.r, base.g, base.b, (int) Math.max(0, Math.min(255, base.a * multiplier)));
        ColumnBox box = ColumnBox.of(chunk, column, frame.expansion(column));
        renderer.box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), scratch, scratch, ShapeMode.Both, 0);
    }
}
