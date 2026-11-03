package com.hunterbuddy.modules.flowesp;

import meteordevelopment.meteorclient.renderer.Renderer3D;

interface ColumnPainter {
    void begin(Renderer3D renderer, RenderFrame frame);

    void paint(FlowChunk chunk, FlowColumn column, double fade, double distanceRatio);
}
