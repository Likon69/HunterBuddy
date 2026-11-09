package com.hunterbuddy.journal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.hunterbuddy.chatdock.DockPlacement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

final class JournalModelTest {
    @Test
    void coordinateFormatsAreHiddenIncludingNegativesDecimalsAndRelativePositions() {
        for (String input : List.of("123.4 56.0 -789.1", "at 123.4 56.0 -789.1", "(123.4, 56.0, -789.1)",
            "x=123.4 y=56.0 z=-789.1", "BetterBlockPos{x=123.4, y=56.0, z=-789.1}",
            "Vec3d[123.4, 56.0, -789.1]", "X: 123.4 Y: 56.0 Z: -789.1", "123.4 / 56.0 / -789.1",
            "\"x\":123.4,\"y\":56.0,\"z\":-789.1", "posX=123.4 posY=56.0 posZ=-789.1")) {
            String hidden = JournalText.redact(input);
            assertFalse(hidden.contains("123.4"), input);
            assertFalse(hidden.contains("56.0"), input);
            assertFalse(hidden.contains("789.1"), input);
        }
        assertEquals("[coords]", JournalText.redact("~123 ~56 ^789"));
        assertEquals("at [coords]", JournalText.redact("at 1.234e2 5.6e1 -7.891e2"));
        assertEquals("XZ: [coords]", JournalText.redact("XZ: 123.4, -789.1"));
        assertEquals("at [coords]", JournalText.redact("at 1,234.5 64 -9,876.5"));
        assertEquals("x=[hidden] y=[hidden] z=[hidden]", JournalText.redact("x=1,234.5 y=64 z=-9,876.5"));
        assertEquals("[coords]", JournalText.redact("1_234 64 -9_876"));
        assertEquals("Minecraft 1.21.11, task 42", JournalText.redact("Minecraft 1.21.11, task 42"));
    }

    @Test
    void normalizationProducesOneCleanLineWithoutColorOrAnsiCodes() {
        String raw = "[Meteor] \u00a7cWarning\u001b[31m\r\nMore\t text\u2028last";
        assertEquals("Warning More text last", JournalText.clean(raw));
    }

    @Test
    void bufferIsBoundedResizingKeepsNewestAndClearDoesNotReuseIds() {
        JournalStore store = new JournalStore(3);
        for (int i = 0; i < 10; i++) store.add(i, JournalSource.METEOR, JournalEntry.Level.INFO, "message " + i);
        JournalEntry[] snapshot = store.snapshot().entries();
        assertEquals(3, snapshot.length);
        assertEquals("message 7", snapshot[0].message());
        store.resize(2);
        assertEquals("message 8", store.snapshot().entries()[0].message());
        long latest = store.snapshot().entries()[1].id();
        store.clear();
        assertEquals(0, store.snapshot().entries().length);
        store.add(0, JournalSource.BARITONE, JournalEntry.Level.WARN, "next");
        assertTrue(store.snapshot().entries()[0].id() > latest);
    }

    @Test
    void longMessagesHaveABoundedStorageCost() {
        JournalStore store = new JournalStore(1);
        store.add(0, JournalSource.OTHER, JournalEntry.Level.ERROR, "x".repeat(50000));
        assertEquals(8195, store.snapshot().entries()[0].message().length());
    }

    @Test
    void maximumLengthNumericRunsAreMaskedWithoutRecursiveOverflow() {
        assertEquals("[coords]", JournalText.redact("1 ".repeat(4096).strip()));
    }

    @Test
    void draggingInEitherDirectionCopiesMessagesOnceAndUsesCurrentPrivacy() {
        JournalStore store = new JournalStore(8);
        for (int i = 0; i < 4; i++) store.add(i, JournalSource.HUNTERBUDDY, JournalEntry.Level.INFO, "at 123.4 56.0 -789.1");
        JournalEntry[] entries = store.snapshot().entries();
        JournalSelection selection = new JournalSelection();
        selection.start(entries[3].id(), false);
        selection.extend(entries[1].id());
        String copied = selection.copy(entries, true);
        assertEquals(3, copied.lines().count());
        assertFalse(copied.contains("123.4"));
        assertTrue(selection.copy(entries, false).contains("123.4"));
        assertFalse(selection.copy(entries, true).contains("123.4"));
        selection.clear();
        assertEquals("", selection.copy(entries, true));
    }

    @Test
    void exportedFilesAreUniqueCleanAndUseTheSamePrivacyAsCopy(@TempDir Path game) throws Exception {
        JournalEntry entry = new JournalEntry(1, 0, JournalSource.BARITONE, JournalEntry.Level.INFO,
            "[Baritone] at 123.4 56.0 -789.1\nnext line");
        Path masked = JournalExport.save(game, new JournalEntry[]{entry}, true);
        Path visible = JournalExport.save(game, new JournalEntry[]{entry}, false);
        assertEquals(game.resolve("hunterbuddy/logs"), masked.getParent());
        assertNotEquals(masked, visible);
        String hidden = Files.readString(masked);
        assertEquals(1, hidden.lines().count());
        assertTrue(hidden.matches("\\d{2}:\\d{2}:\\d{2} \\[Baritone] at \\[coords] next line\\n"));
        assertTrue(Files.readString(visible).contains("123.4"));
        JournalSelection selection = new JournalSelection();
        selection.start(entry.id(), false);
        assertEquals(selection.copy(new JournalEntry[]{entry}, true) + "\n", hidden);
    }

    @Test
    void wrappedRowsRebuildOnPrivacyChangeAndCacheUnchangedSnapshots() {
        JournalStore store = new JournalStore(2);
        store.add(0, JournalSource.METEOR, JournalEntry.Level.INFO, "Position at 123.4 56.0 -789.1 with more words");
        JournalRows rows = new JournalRows();
        JournalBuffer buffer = new JournalBuffer();
        store.read(buffer);
        assertTrue(rows.update(buffer, false, 12, 1, 0, 100, String::length, text -> text));
        int visibleParts = rows.size();
        assertTrue(visibleParts > 1);
        assertFalse(rows.update(buffer, false, 12, 1, 0, 100, text -> { fail("Unchanged text measured again"); return 0; }, text -> text));
        assertTrue(rows.update(buffer, true, 12, 1, 0, 100, String::length, text -> text));
        for (int i = 0; i < rows.size(); i++) assertFalse(rows.get(i).message().contains("123.4"));
        assertTrue(rows.size() <= visibleParts);
        rows.clear();
        assertEquals(0, rows.size());
    }

    @Test
    void wrappingDoesNotCutSurrogatePairs() {
        String text = "one \ud83d\ude80 two longword";
        for (String line : JournalRows.wrap(text, 5, String::length)) {
            assertFalse(Character.isHighSurrogate(line.charAt(line.length() - 1)));
            assertFalse(Character.isLowSurrogate(line.charAt(0)));
        }
    }

    @Test
    void chatButtonAvoidsTheCrosshairAndTheChatArea() {
        for (double scale : new double[]{0.6, 1, 1.8}) {
            var button = DockPlacement.initial(1920, 1080, 90 * scale, 34 * scale, 984, 420, 54);
            assertFalse(button.contains(960, 540));
            assertTrue(button.x() >= 984 || button.y() + button.h() < 420);
        }
        var beside = DockPlacement.initial(1920, 1080, 90, 34, 1000, 0, 54);
        assertTrue(beside.x() >= 1000);
        assertFalse(beside.contains(960, 540));
        var largeText = DockPlacement.initial(1920, 1080, 110, 42, 0, 1080, 54);
        assertTrue(largeText.w() >= 110 && largeText.h() >= 42);
        assertTrue(largeText.y() + largeText.h() <= 540 - 54);
    }
}
