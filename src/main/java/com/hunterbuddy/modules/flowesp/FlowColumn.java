package com.hunterbuddy.modules.flowesp;

record FlowColumn(ColumnPos pos, Span span, int flowLength, ColumnFlags flags) {
    record ColumnPos(int localX, int localZ) {
    }

    record Span(int bottomY, int topY) {
        int height() {
            return topY - bottomY + 1;
        }
    }

    record ColumnFlags(boolean lava, boolean hasSource, boolean roof, boolean tip) {
    }
}
