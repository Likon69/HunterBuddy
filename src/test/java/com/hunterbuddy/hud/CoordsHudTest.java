package com.hunterbuddy.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CoordsHudTest {
    @Test
    void roundsSignedCoordinatesAndKeepsTrailingDecimals() {
        CoordsHud.Row row = new CoordsHud.Row();
        row.update(-1234.567, 64, 9876.543, 2, true);
        assertEquals("-1234.57, 64.00, 9876.54", row.text);
        row.update(-0.0001, 64, -1.9999, 3, true);
        assertEquals("0.000, 64.000, -2.000", row.text);
    }

    @Test
    void hidesYAndReformatsWhenSettingsChange() {
        CoordsHud.Row row = new CoordsHud.Row();
        row.update(1234.5, 64, -9876.5, 0, false);
        assertEquals("1235, -9876", row.text);
        row.update(1234.5, 64, -9876.5, 1, true);
        assertEquals("1234.5, 64.0, -9876.5", row.text);
    }

    @Test
    void unchangedRoundedCoordinatesReuseTheSameString() {
        CoordsHud.Row row = new CoordsHud.Row();
        row.update(1234.51, 64, -9876.51, 1, false);
        String text = row.text;
        row.update(1234.52, 70, -9876.52, 1, false);
        assertSame(text, row.text);
    }

    @Test
    void largeOppositeCoordinatesKeepAllDigitsWithoutIntegerOverflow() {
        CoordsHud.Row row = new CoordsHud.Row();
        row.update(-239999992, -64, 239999992, 3, true);
        assertEquals("-239999992.000, -64.000, 239999992.000", row.text);
    }
}