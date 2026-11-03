package com.hunterbuddy.modules.flowesp;

record ColumnBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    static ColumnBox of(FlowChunk chunk, FlowColumn column, double expansion) {
        double x = chunk.pos().getStartX() + column.pos().localX();
        double z = chunk.pos().getStartZ() + column.pos().localZ();
        return new ColumnBox(x - expansion, column.span().bottomY() - expansion, z - expansion,
            x + 1 + expansion, column.span().topY() + 1 + expansion, z + 1 + expansion);
    }

    double bottomCenterX() {
        return (minX + maxX) / 2.0;
    }

    double bottomCenterZ() {
        return (minZ + maxZ) / 2.0;
    }
}
