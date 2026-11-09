package com.hunterbuddy.journal;

import com.hunterbuddy.chatdock.DockDrag;
import com.hunterbuddy.chatdock.DockPlacement;
import com.hunterbuddy.hud.glass.BlurCadence;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class JournalPerformanceTest {
    @Test
    void incrementalReaderMatchesSnapshotsAcrossOverflowClearAndResize() {
        JournalStore store = new JournalStore(31);
        JournalBuffer buffer = new JournalBuffer();
        Random random = new Random(42);
        for (int round = 0; round < 1000; round++) {
            if (round % 29 == 0) store.clear();
            if (round % 17 == 0) store.resize(1 + random.nextInt(100));
            for (int i = random.nextInt(200); i > 0; i--) store.add(i, JournalSource.BARITONE, JournalEntry.Level.INFO, "flight " + round + " segment " + i);
            store.read(buffer);
            JournalEntry[] snapshot = store.snapshot().entries();
            assertEquals(snapshot.length, buffer.size());
            for (int i = 0; i < snapshot.length; i++) assertSame(snapshot[i], buffer.get(i));
            assertFalse(store.read(buffer));
            if (round % 13 == 0) buffer.clear();
        }
    }

    @Test
    void onlyViewportMessagesAreMeasuredAndPausedScrollSurvivesNewEntries() {
        JournalStore store = new JournalStore(8192);
        for (int i = 0; i < 8192; i++) store.add(i, JournalSource.METEOR, JournalEntry.Level.INFO, "message " + i);
        JournalBuffer buffer = new JournalBuffer();
        store.read(buffer);
        JournalRows rows = new JournalRows();
        AtomicInteger calls = new AtomicInteger();
        rows.update(buffer, false, 100, 1, 0, 20, text -> { calls.incrementAndGet(); return text.length(); }, text -> text);
        assertEquals(20, rows.size());
        assertEquals("message 8172", rows.get(0).message());
        assertTrue(calls.get() < 200);
        int measured = calls.get();
        assertFalse(rows.update(buffer, false, 100, 1, 0, 20, text -> { fail("Unchanged viewport measured"); return 0; }, text -> text));
        assertEquals(measured, calls.get());
        rows.home();
        rows.update(buffer, false, 100, 1, 0, 20, String::length, text -> text);
        assertEquals("message 0", rows.get(0).message());
        rows.scroll(30);
        rows.update(buffer, false, 100, 1, 0, 20, String::length, text -> text);
        long anchor = rows.get(0).entry().id();
        store.add(0, JournalSource.BARITONE, JournalEntry.Level.INFO, "new message");
        store.read(buffer);
        rows.update(buffer, false, 100, 1, 0, 20, String::length, text -> text);
        assertEquals(anchor, rows.get(0).entry().id());
        rows.end();
        rows.update(buffer, false, 100, 1, 0, 20, String::length, text -> text);
        assertEquals("new message", rows.get(19).message());
    }

    @Test
    void wrappedRowsScrollInBothDirectionsAndPrivacyInvalidatesOnlyVisibleText() {
        JournalStore store = new JournalStore(100);
        for (int i = 0; i < 100; i++) store.add(i, JournalSource.METEOR, JournalEntry.Level.INFO, "at 123.4 56.0 -789.1 extra words");
        JournalBuffer buffer = new JournalBuffer();
        store.read(buffer);
        JournalRows rows = new JournalRows();
        rows.update(buffer, false, 12, 1, 0, 5, String::length, text -> text);
        rows.home();
        rows.update(buffer, false, 12, 1, 0, 5, String::length, text -> text);
        assertEquals(0, rows.get(0).part());
        rows.scroll(1);
        rows.update(buffer, false, 12, 1, 0, 5, String::length, text -> text);
        assertEquals(1, rows.get(0).part());
        rows.scroll(-1);
        rows.update(buffer, true, 12, 1, 1, 5, String::length, text -> text);
        assertEquals(0, rows.get(0).part());
        for (int i = 0; i < rows.size(); i++) assertFalse(rows.get(i).message().contains("123.4"));
        store.clear();
        store.read(buffer);
        rows.update(buffer, true, 12, 1, 1, 5, String::length, text -> text);
        assertEquals(0, rows.size());
    }

    @Test
    void singleWrappedMessageHasAScrollbarAndMultipleWheelEventsAccumulate() {
        JournalStore store = new JournalStore(1);
        store.add(0, JournalSource.METEOR, JournalEntry.Level.INFO, "word ".repeat(100));
        JournalBuffer buffer = new JournalBuffer();
        store.read(buffer);
        JournalRows rows = new JournalRows();
        rows.update(buffer, false, 10, 1, 0, 5, String::length, text -> text);
        assertTrue(rows.fraction() < 1);
        rows.home();
        rows.update(buffer, false, 10, 1, 0, 5, String::length, text -> text);
        assertEquals(0, rows.position(), 0.0001);
        rows.scroll(1);
        rows.scroll(1);
        rows.update(buffer, false, 10, 1, 0, 5, String::length, text -> text);
        assertEquals(2, rows.get(0).part());
        assertTrue(rows.position() > 0);
    }

    @Test
    void dragThresholdSeparatesClicksFromMovesAndRelativePositionSurvivesResizing() {
        DockDrag drag = new DockDrag();
        drag.begin(110, 110, 100, 100);
        assertTrue(drag.update(112, 111, false, 1920, 1080, 100, 40, 4));
        assertFalse(drag.moved);
        drag.begin(110, 110, 100, 100);
        assertFalse(drag.update(900, 500, true, 1920, 1080, 100, 40, 4));
        assertTrue(drag.moved);
        assertTrue(drag.update(900, 500, false, 1920, 1080, 100, 40, 4));
        double relative = DockPlacement.fraction(drag.x, 1920, 100);
        assertEquals(relative, DockPlacement.fraction(DockPlacement.coordinate(relative, 2560, 100, 0), 2560, 100), 1e-9);
        assertEquals(420, DockPlacement.coordinate(-1, 1920, 100, 420));
        assertEquals(1820, DockPlacement.coordinate(1, 1920, 100, 0));
        drag.cancel();
        assertFalse(drag.active);
    }

    @Test
    void blurReusesItsCaptureButRefreshesOnResizeReloadAndExplicitInvalidation() {
        BlurCadence cadence = new BlurCadence(125);
        assertTrue(cadence.refresh(1000, 0, 1920, 1080, 2));
        assertFalse(cadence.refresh(1100, 0, 1920, 1080, 2));
        assertTrue(cadence.refresh(1125, 0, 1920, 1080, 2));
        assertTrue(cadence.refresh(1126, 0, 2560, 1440, 2));
        assertTrue(cadence.refresh(1127, 1, 2560, 1440, 2));
        assertTrue(cadence.refresh(1128, 1, 2560, 1440, 3));
        cadence.invalidate();
        assertTrue(cadence.refresh(1129, 1, 2560, 1440, 3));
        BlurCadence live = new BlurCadence(0);
        assertTrue(live.refresh(1000, 0, 1920, 1080, 2));
        assertTrue(live.refresh(1000, 0, 1920, 1080, 2));
    }
}
