package com.hunterbuddy.modules.flowesp;

import net.minecraft.fluid.FluidState;

final class FluidRun {
    private final int bottomY;
    private final boolean lava;
    private int topY;
    private boolean roof;
    private int sources;
    private int flows;
    private int lowestFlowLevel = Integer.MAX_VALUE;

    FluidRun(int startY, boolean lava) {
        this.bottomY = startY;
        this.topY = startY;
        this.lava = lava;
    }

    void add(int y, FluidState fluid, boolean roofCell) {
        topY = y;
        roof |= roofCell;
        if (fluid.isStill()) {
            sources++;
        } else {
            flows++;
            lowestFlowLevel = Math.min(lowestFlowLevel, fluid.getLevel());
        }
    }

    boolean hasFlow() {
        return flows > 0;
    }

    int sources() {
        return sources;
    }

    int flows() {
        return flows;
    }

    FlowColumn toColumn(int localX, int localZ, int tipLevel) {
        FlowColumn.ColumnFlags flags = new FlowColumn.ColumnFlags(lava, sources > 0, roof, lowestFlowLevel <= tipLevel);
        return new FlowColumn(new FlowColumn.ColumnPos(localX, localZ), new FlowColumn.Span(bottomY, topY), flows, flags);
    }
}
