package com.hunterbuddy.hud.toast;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ModuleToastInboxTest {
    @Test
    void fiveChangesAreAllDeliveredInOrderEvenWithOneVisibleCardAndLongDuration() {
        ModuleToastHistory history = new ModuleToastHistory();
        ModuleToastInbox inbox = new ModuleToastInbox(history);
        for (int i = 0; i < 5; i++) history.record(new ModuleToastTimeline.Notice("Module " + i, "R", i % 2 == 0));
        inbox.read(false);
        List<String> shown = new ArrayList<>();
        for (int second = 0; second <= 75; second++) {
            inbox.timeline.update(second * 1_000_000_000L, 1, 15);
            if (second % 15 == 0 && second < 75) shown.add(inbox.timeline.get(0).notice.name());
        }
        assertEquals(List.of("Module 0", "Module 1", "Module 2", "Module 3", "Module 4"), shown);
        assertEquals(0, inbox.timeline.size());
    }

    @Test
    void unboundGuiNoticeObeysBoundOnly() {
        ModuleToastHistory history = new ModuleToastHistory();
        ModuleToastInbox all = new ModuleToastInbox(history);
        ModuleToastInbox bound = new ModuleToastInbox(history);
        history.record(new ModuleToastTimeline.Notice("Gui Module", "Unbound", true));
        all.read(false);
        bound.read(true);
        all.timeline.update(0, 3, 4);
        bound.timeline.update(0, 3, 4);
        assertEquals("Unbound", all.timeline.get(0).notice.bind());
        assertEquals(0, bound.timeline.size());
    }

    @Test
    void reconnectClearsOldCardsAndKeepsFreshNewSessionChanges() {
        ModuleToastHistory history = new ModuleToastHistory();
        ModuleToastInbox inbox = new ModuleToastInbox(history);
        history.record(new ModuleToastTimeline.Notice("Old", "R", true));
        inbox.read(false);
        inbox.timeline.update(0, 3, 4);
        assertEquals(1, inbox.timeline.size());
        history.reset();
        history.record(new ModuleToastTimeline.Notice("New", "G", true));
        inbox.read(false);
        inbox.timeline.update(2, 3, 4);
        assertEquals(1, inbox.timeline.size());
        assertEquals("New", inbox.timeline.get(0).notice.name());
        assertNull(history.get(1));
        inbox.read(false);
        inbox.timeline.update(3, 3, 4);
        assertEquals(1, inbox.timeline.size());
    }

    @Test
    void newHudIgnoresPastHistoryButItsWaitingEventsAreNotLost() {
        ModuleToastHistory history = new ModuleToastHistory();
        history.record(new ModuleToastTimeline.Notice("Past", "R", true));
        ModuleToastInbox inbox = new ModuleToastInbox(history);
        inbox.read(false);
        inbox.timeline.update(0, 3, 4);
        assertEquals(0, inbox.timeline.size());
        history.record(new ModuleToastTimeline.Notice("Unread", "R", false));
        inbox.read(false);
        inbox.timeline.update(9_000_000_000L, 3, 4);
        assertEquals("Unread", inbox.timeline.get(0).notice.name());
    }
}
