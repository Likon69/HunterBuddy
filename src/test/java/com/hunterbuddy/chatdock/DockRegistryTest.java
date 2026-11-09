package com.hunterbuddy.chatdock;

import com.hunterbuddy.chatdock.entries.LogDockEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class DockRegistryTest {
    @Test
    void explicitRegistryContainsTheLogEntryAndKeepsStableInstances() {
        DockRegistry registry = new DockRegistry();
        DockEntry[] entries = registry.entries();
        assertSame(entries, registry.entries());
        assertTrue(java.util.Arrays.stream(entries).anyMatch(entry -> entry instanceof LogDockEntry && entry.id().equals("log")));
    }
}
