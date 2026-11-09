package com.hunterbuddy.hud.glass;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class GlassCornersTest {
    @Test
    void cornerErrorStaysBelowAFractionOfAPixelAtSmallAndLargeScales() {
        for (double scale : new double[] {0.5, 0.7, 1, 1.8, 2}) {
            for (double radius : new double[] {1.5, 5, 9, 32, 128}) {
                double pixels = radius * scale;
                int segments = GlassCorners.segments(pixels);
                double chordError = pixels * (1 - Math.cos(Math.PI / (4 * segments)));
                assertTrue(segments >= 8 && segments <= 64);
                assertTrue(chordError <= 0.041, pixels + ": " + chordError);
            }
        }
        assertTrue(GlassCorners.segments(18) > GlassCorners.segments(3.5));
    }

    @Test
    void allQuarterArcsMeetWithoutSnappingOrBeveledEndpoints() {
        int count = GlassCorners.segments(6.3);
        for (int corner = 0; corner < 4; corner++) {
            int next = (corner + 1) % 4;
            assertEquals(GlassCorners.x(corner, count, count), GlassCorners.x(next, count, 0), 0.000001);
            assertEquals(GlassCorners.y(corner, count, count), GlassCorners.y(next, count, 0), 0.000001);
            for (int i = 0; i <= count; i++) {
                double x = GlassCorners.x(corner, count, i);
                double y = GlassCorners.y(corner, count, i);
                assertEquals(1, x * x + y * y, 0.000001);
            }
        }
    }
}
