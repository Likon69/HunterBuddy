package com.hunterbuddy.hud.system;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SystemStatsSamplerTest {
    @Test
    void samplesNoMoreThanTwicePerSecondAndDoesNotCatchUpAfterAPause() {
        FakeSource source = new FakeSource();
        SystemStatsSampler sampler = new SystemStatsSampler(source);
        assertTrue(sampler.refresh(0));
        for (long now = 1; now < 500_000_000; now += 1_000_000) assertFalse(sampler.refresh(now));
        assertEquals(1, source.reads);
        assertTrue(sampler.refresh(500_000_000));
        assertTrue(sampler.refresh(60_000_000_000L));
        assertFalse(sampler.refresh(60_000_000_001L));
        assertEquals(3, source.reads);
        assertEquals(3, sampler.count());
    }

    @Test
    void historyKeepsExactlyTheLatestSixtyValuesInOrder() {
        SystemStatsSampler sampler = new SystemStatsSampler(new FakeSource());
        for (int i = 0; i < 100; i++) sampler.record(i, 100, 100 - i);
        assertEquals(60, sampler.count());
        for (int age = 0; age < 60; age++) {
            assertEquals(99 - age, sampler.history(true, age));
            assertEquals(1 + age, sampler.history(false, age));
        }
        assertEquals(-1, sampler.history(true, 60));
    }

    @Test
    void missingCpuDataDoesNotBecomeZeroOrKeepAnOldReading() {
        SystemStatsSampler sampler = new SystemStatsSampler(new FakeSource());
        sampler.record(2L << 30, 8L << 30, 12.34);
        assertEquals("2.0 / 8.0 GiB (25%)", sampler.ramText());
        assertEquals("12.3%", sampler.cpuText());
        for (double unavailable : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            sampler.record(0, 0, unavailable);
            assertEquals("--", sampler.cpuText());
            assertEquals(-1, sampler.history(false, 0));
            assertEquals("--", sampler.ramText());
        }
    }

    @Test
    void unchangedFormattedReadingsReuseTheirStringsAndPercentagesStayBounded() {
        SystemStatsSampler sampler = new SystemStatsSampler(new FakeSource());
        sampler.record(2L << 30, 8L << 30, 12.31);
        String ram = sampler.ramText();
        String cpu = sampler.cpuText();
        sampler.record((2L << 30) + 1, 8L << 30, 12.32);
        assertSame(ram, sampler.ramText());
        assertSame(cpu, sampler.cpuText());
        sampler.record(Long.MAX_VALUE, 8L << 30, 200);
        assertEquals(100, sampler.ramUsage());
        assertEquals(100, sampler.cpuUsage());
        sampler.record(-10, 8L << 30, -10);
        assertEquals(0, sampler.ramUsage());
        sampler.record(Long.MAX_VALUE, Long.MAX_VALUE, 0);
        assertEquals(10, sampler.ramDigits());
        assertEquals("8589934592.0 / 8589934592.0 GiB (100%)", sampler.ramText());
    }

    private static final class FakeSource implements SystemStatsSampler.Source {
        int reads;
        @Override
        public long usedMemory() { reads++; return 2L << 30; }
        @Override
        public long maxMemory() { return 8L << 30; }
        @Override
        public double processCpu() { return 12.3; }
    }
}
