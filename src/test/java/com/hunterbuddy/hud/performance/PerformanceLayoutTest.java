package com.hunterbuddy.hud.performance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PerformanceLayoutTest {
    private static final double[] LABELS = {18, 24, 18};
    private static final double[] VALUES = {48, 84, 48};

    @Test
    void normalAndCompactLayoutsKeepThreeSeparateCapsulesInsideTheGlassFrame() {
        PerformanceLayout layout = new PerformanceLayout();
        for (boolean compact : new boolean[] {false, true}) {
            for (double scale : new double[] {0.5, 1, 2}) {
                layout.update(scale, compact, false, 12, 18, 0, 0, LABELS, VALUES);
                double left = layout.padding;
                for (int i = 0; i < 3; i++) {
                    assertTrue(left >= layout.padding);
                    assertTrue(left + layout.widths[i] <= layout.width - layout.padding + 0.00001);
                    double labelEnd = left + layout.labelInset + LABELS[i];
                    double valueStart = left + layout.widths[i] - layout.valueInset - VALUES[i];
                    assertTrue(valueStart - labelEnd >= 12 * scale - 0.00001);
                    left += layout.widths[i] + layout.gap;
                }
                assertTrue(layout.gap >= 5);
                assertTrue(layout.padding >= 5);
                assertTrue(layout.rowHeight >= 18);
                assertEquals(layout.padding, layout.height - layout.rowY - layout.rowHeight, 0.00001);
            }
        }
    }

    @Test
    void compactTitleSitsToTheLeftWithoutAddingAnExtraRow() {
        PerformanceLayout layout = new PerformanceLayout();
        layout.update(1, true, false, 12, 18, 15, 90, LABELS, VALUES);
        double height = layout.height;
        double width = layout.width;
        layout.update(1, true, true, 12, 18, 15, 90, LABELS, VALUES);
        assertEquals(height, layout.height);
        assertEquals(width + 102, layout.width);
        assertEquals(102, layout.inlineTitle);
        assertEquals(layout.padding, layout.rowY);
    }

    @Test
    void realFontMetricsExpandTheFrameInsteadOfClippingLargerGlyphs() {
        PerformanceLayout layout = new PerformanceLayout();
        layout.update(1, false, true, 12, 18, 15, 90, LABELS, VALUES);
        double normalWidth = layout.width;
        double normalHeight = layout.height;
        layout.update(1, false, true, 16, 28, 20, 120,
            new double[] {24, 32, 24}, new double[] {64, 112, 64});
        assertTrue(layout.width > normalWidth);
        assertTrue(layout.height > normalHeight);
        assertTrue(layout.rowHeight >= 28 + 14);
        assertTrue(layout.rowY >= 20 + 6 + layout.padding);
    }
}
