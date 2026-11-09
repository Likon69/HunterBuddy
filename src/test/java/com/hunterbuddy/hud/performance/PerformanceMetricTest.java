package com.hunterbuddy.hud.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class PerformanceMetricTest {
    @Test
    void unchangedReadingsReuseFormattedText() {
        PerformanceMetric ping = new PerformanceMetric("Ping", "8888 ms", " ms");
        ping.integer(145);
        String text = ping.text;
        ping.integer(145);
        assertSame(text, ping.text);
        assertEquals("145 ms", ping.text);
    }

    @Test
    void unavailableReadingsKeepTheDash() {
        PerformanceMetric ping = new PerformanceMetric("Ping", "8888 ms", " ms");
        ping.integer(-1);
        assertEquals("-", ping.text);
        PerformanceMetric tps = new PerformanceMetric("TPS", "88.8", "");
        tps.decimal(-1);
        assertEquals("-", tps.text);
    }

    @Test
    void decimalReadingsKeepOneDecimalAndReuseUnchangedText() {
        PerformanceMetric tps = new PerformanceMetric("TPS", "88.8", "");
        tps.decimal(19.75f);
        assertEquals("19.8", tps.text);
        String text = tps.text;
        tps.decimal(19.75f);
        assertSame(text, tps.text);
        tps.decimal(-0.0f);
        assertEquals("-0.0", tps.text);
    }
}
