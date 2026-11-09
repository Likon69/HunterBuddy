package com.hunterbuddy.gui.theme.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class HbItemClipTest {
    @Test
    void partialWindowOverlapPreservesOnlyTheVisibleIconArea() {
        HbItemClip clip = new HbItemClip();
        clip.reset(0, 0, 16, 16);
        clip.exclude(8, 0, 16, 16);
        assertEquals(1, clip.size());
        assertEquals(0, clip.edge(0, 0));
        assertEquals(8, clip.edge(0, 2));
        assertEquals(128, area(clip));
        clip.exclude(0, 0, 16, 16);
        assertEquals(0, clip.size());
    }

    @Test
    void overlappingForegroundWindowsNeverRestoreAlreadyHiddenPixels() {
        HbItemClip clip = new HbItemClip();
        clip.reset(0, 0, 16, 16);
        clip.exclude(4, 4, 8, 8);
        assertEquals(192, area(clip));
        clip.exclude(8, 0, 16, 16);
        assertEquals(96, area(clip));
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int covers = 0;
                for (int i = 0; i < clip.size(); i++) {
                    if (x + 0.5 >= clip.edge(i, 0) && x + 0.5 < clip.edge(i, 2)
                        && y + 0.5 >= clip.edge(i, 1) && y + 0.5 < clip.edge(i, 3)) covers++;
                }
                assertEquals(x < 8 && !(x >= 4 && y >= 4 && y < 12) ? 1 : 0, covers);
            }
        }
    }

    @Test
    void movingTheIconResetsOldMasksAndDisjointWindowsDoNotClipIt() {
        HbItemClip clip = new HbItemClip();
        clip.reset(0, 0, 16, 16);
        clip.exclude(0, 0, 16, 16);
        clip.reset(20, 30, 16, 16);
        clip.exclude(0, 0, 16, 16);
        assertEquals(1, clip.size());
        assertEquals(20, clip.edge(0, 0));
        assertEquals(30, clip.edge(0, 1));
        assertEquals(256, area(clip));
    }

    private static double area(HbItemClip clip) {
        double area = 0;
        for (int i = 0; i < clip.size(); i++)
            area += (clip.edge(i, 2) - clip.edge(i, 0)) * (clip.edge(i, 3) - clip.edge(i, 1));
        return area;
    }
}
