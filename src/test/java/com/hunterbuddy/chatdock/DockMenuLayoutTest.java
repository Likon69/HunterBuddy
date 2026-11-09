package com.hunterbuddy.chatdock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class DockMenuLayoutTest {
    @Test
    void opensBelowAndMovesAboveWhenTheBottomEdgeWouldClip() {
        DockMenuLayout layout = new DockMenuLayout();
        layout.update(100, 100, 52, 800, 600, 116, 32, 1, 1, 1);
        assertFalse(layout.above);
        assertTrue(layout.y > 152);
        layout.update(100, 540, 52, 800, 600, 116, 32, 1, 1, 1);
        assertTrue(layout.above);
        assertTrue(layout.y + layout.height < 540);
    }

    @Test
    void keepsTheListOnScreenAtBothHorizontalEdges() {
        DockMenuLayout layout = new DockMenuLayout();
        for (double x : new double[] {-10, 0, 799, 900}) {
            layout.update(x, 200, 52, 800, 600, 116, 32, 1, 1, 1);
            assertTrue(layout.x >= 0);
            assertTrue(layout.x + layout.width <= 800);
        }
    }

    @Test
    void animatedRowsAndClickTargetsMoveTogetherAndPaddingIsNotAnEntry() {
        DockMenuLayout layout = new DockMenuLayout();
        for (double progress : new double[] {0, 0.25, 0.75, 1}) {
            layout.update(100, 100, 52, 800, 600, 116, 32, 2, 1, progress);
            double x = layout.x + layout.width / 2;
            assertEquals(-1, layout.hit(x, layout.y + 1, 2));
            assertEquals(0, layout.hit(x, layout.y + layout.padding + 16, 2));
            assertEquals(1, layout.hit(x, layout.y + layout.padding + 48, 2));
            assertEquals(-1, layout.hit(x, layout.y + layout.height - 1, 2));
            assertEquals(-1, layout.hit(layout.x - 1, layout.y + 20, 2));
        }
    }
}
