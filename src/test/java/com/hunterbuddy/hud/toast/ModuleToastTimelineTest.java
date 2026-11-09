package com.hunterbuddy.hud.toast;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ModuleToastTimelineTest {
    @Test
    void preservesRapidOnOffChangesAndStaggersTheirEntry() {
        ModuleToastTimeline timeline = new ModuleToastTimeline();
        timeline.offer(new ModuleToastTimeline.Notice("Flight", "Ctrl + R", true));
        timeline.offer(new ModuleToastTimeline.Notice("Flight", "Ctrl + R", false));
        timeline.update(0, 3, 4);
        assertEquals(1, timeline.size());
        assertTrue(timeline.get(0).notice.enabled());
        timeline.update(159_000_000L, 3, 4);
        assertEquals(1, timeline.size());
        timeline.update(160_000_000L, 3, 4);
        assertEquals(2, timeline.size());
        assertFalse(timeline.get(1).notice.enabled());
        assertEquals("Ctrl + R", timeline.get(1).notice.bind());
    }

    @Test
    void waitingCardsReceiveTheirOwnFullLifetime() {
        ModuleToastTimeline timeline = new ModuleToastTimeline();
        timeline.offer(new ModuleToastTimeline.Notice("First", "R", true));
        timeline.offer(new ModuleToastTimeline.Notice("Second", "G", true));
        timeline.update(0, 1, 4);
        timeline.update(3_999_000_000L, 1, 4);
        assertEquals("First", timeline.get(0).notice.name());
        timeline.update(4_000_000_000L, 1, 4);
        assertEquals("Second", timeline.get(0).notice.name());
        assertEquals(0, timeline.get(0).opacity(4_000_000_000L, 4));
        assertEquals(1, timeline.get(0).opacity(5_000_000_000L, 4));
        timeline.update(8_000_000_000L, 1, 4);
        assertEquals(0, timeline.size());
    }

    @Test
    void fadesContinuouslyAndCompactsRowsWithoutSnapping() {
        ModuleToastTimeline timeline = new ModuleToastTimeline();
        timeline.offer(new ModuleToastTimeline.Notice("First", "R", true));
        timeline.offer(new ModuleToastTimeline.Notice("Second", "G", false));
        timeline.update(0, 3, 4);
        timeline.update(200_000_000L, 3, 4);
        assertTrue(timeline.get(0).opacity(100_000_000L, 4) > 0);
        assertTrue(timeline.get(0).opacity(100_000_000L, 4) < 1);
        double earlier = timeline.get(0).opacity(3_500_000_000L, 4);
        double later = timeline.get(0).opacity(3_900_000_000L, 4);
        assertTrue(earlier > later && later > 0);
        timeline.update(4_000_000_000L, 3, 4);
        assertEquals("Second", timeline.get(0).notice.name());
        assertTrue(timeline.get(0).row > 0 && timeline.get(0).row < 1);
    }

    @Test
    void boundsBurstsPreservesAcceptedEventsAndResetsCleanly() {
        ModuleToastTimeline timeline = new ModuleToastTimeline();
        for (int i = 0; i < 1000; i++) timeline.offer(new ModuleToastTimeline.Notice(Integer.toString(i), "R", true));
        timeline.update(0, 3, 4);
        assertEquals("968", timeline.get(0).notice.name());
        timeline.update(9_000_000_000L, 3, 4);
        assertEquals("969", timeline.get(0).notice.name());
        timeline.clear();
        timeline.offer(new ModuleToastTimeline.Notice("New", "Mouse 4", false));
        timeline.update(9_000_000_001L, 3, 4);
        assertEquals("New", timeline.get(0).notice.name());
        timeline.clear();
        assertEquals(0, timeline.size());
        timeline.update(10_000_000_000L, 3, 4);
        assertEquals(0, timeline.size());
    }

    @Test
    void handlesVisibleLimitChangesAndCanRunForThirtyMinutes() {
        ModuleToastTimeline timeline = new ModuleToastTimeline();
        long last = 0;
        for (int tick = 0; tick < 36000; tick++) {
            long now = tick * 50_000_000L;
            if (tick % 9 == 0) timeline.offer(new ModuleToastTimeline.Notice("Module", "R", tick % 18 == 0));
            int maximum = tick % 100 < 50 ? 6 : 1;
            timeline.update(now, maximum, 2);
            assertTrue(timeline.size() <= maximum);
            for (int i = 0; i < timeline.size(); i++) {
                ModuleToastTimeline.Slot slot = timeline.get(i);
                assertTrue(slot.started <= now);
                assertTrue(slot.opacity(now, 2) >= 0 && slot.opacity(now, 2) <= 1);
            }
            last = now;
        }
        for (int i = 1; i <= 400; i++) timeline.update(last + i * 50_000_000L, 6, 2);
        assertEquals(0, timeline.size());
    }
}
