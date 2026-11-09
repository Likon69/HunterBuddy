package com.hunterbuddy.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActionSwapTest {
    @Test
    void restoresBetweenMiningAndCombatActions() {
        Slots slots = new Slots();
        ActionSwap.perform(() -> slots.select(2), () -> slots.send("START"), () -> slots.select(0));
        slots.sync();
        assertEquals(0, slots.selected);
        assertEquals(0, slots.server);
        ActionSwap.perform(() -> slots.select(5), () -> {
            slots.sync();
            slots.send("ATTACK");
        }, () -> slots.select(0));
        ActionSwap.perform(() -> slots.select(2), () -> slots.send("STOP"), () -> slots.select(0));
        assertEquals(List.of("SLOT 2", "START 2", "SLOT 0", "SLOT 5", "ATTACK 5", "SLOT 0", "SLOT 2", "STOP 2", "SLOT 0"), slots.packets);
        assertEquals(0, slots.selected);
        assertEquals(0, slots.server);
        assertEquals(0, slots.cached);
        assertFalse(ActionSwap.active());
    }

    @Test
    void restoresAfterActionThrows() {
        Slots slots = new Slots();
        RuntimeException failure = new IllegalStateException("action failed");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> ActionSwap.perform(
            () -> slots.select(2), () -> { throw failure; }, () -> slots.select(0))));
        assertEquals(List.of("SLOT 2", "SLOT 0"), slots.packets);
        assertEquals(0, slots.selected);
        assertEquals(0, slots.server);
        assertEquals(0, slots.cached);
        assertFalse(ActionSwap.active());
    }

    @Test
    void restoresAfterEquipThrows() {
        Slots slots = new Slots();
        assertThrows(IllegalStateException.class, () -> ActionSwap.perform(() -> {
            slots.select(2);
            throw new IllegalStateException("equip failed");
        }, () -> fail("action must not run"), () -> slots.select(0)));
        assertEquals(0, slots.selected);
        assertEquals(0, slots.server);
        assertFalse(ActionSwap.active());
    }

    @Test
    void nestedActionsRestoreTheOuterSlotAndGuard() {
        Slots slots = new Slots();
        ActionSwap.perform(() -> slots.select(2), () -> {
            ActionSwap.perform(() -> slots.select(5), () -> slots.send("ATTACK"), () -> slots.select(2));
            assertTrue(ActionSwap.active());
            assertEquals(2, slots.selected);
            assertEquals(2, slots.server);
        }, () -> slots.select(0));
        assertFalse(ActionSwap.active());
        assertEquals(0, slots.selected);
        assertEquals(0, slots.server);
    }

    @Test
    void clearsGuardWhenRestoreThrows() {
        assertThrows(IllegalStateException.class, () -> ActionSwap.perform(() -> {}, () -> {}, () -> {
            throw new IllegalStateException("restore failed");
        }));
        assertFalse(ActionSwap.active());
    }

    @Test
    void inventorySwapRestoresContentsWithoutSelecting() {
        Slots slots = new Slots();
        String[] inventory = {"held", "tool"};
        Runnable swap = () -> {
            String held = inventory[0];
            inventory[0] = inventory[1];
            inventory[1] = held;
            slots.packets.add("SWAP");
        };
        ActionSwap.perform(swap, () -> {
            assertEquals("tool", inventory[0]);
            slots.send("STOP");
        }, swap);
        assertEquals(List.of("SWAP", "STOP 0", "SWAP"), slots.packets);
        assertEquals("held", inventory[0]);
        assertEquals("tool", inventory[1]);
        assertEquals(0, slots.selected);
        assertEquals(0, slots.server);
    }

    @Test
    void inventorySwapRestoresContentsAfterActionThrows() {
        String[] inventory = {"held", "tool"};
        Runnable swap = () -> {
            String held = inventory[0];
            inventory[0] = inventory[1];
            inventory[1] = held;
        };
        assertThrows(IllegalStateException.class, () -> ActionSwap.perform(swap, () -> {
            throw new IllegalStateException("action failed");
        }, swap));
        assertEquals("held", inventory[0]);
        assertEquals("tool", inventory[1]);
        assertFalse(ActionSwap.active());
    }

    private static final class Slots {
        private final List<String> packets = new ArrayList<>();
        private int selected;
        private int cached;
        private int server;

        private void select(int slot) {
            assertTrue(ActionSwap.active());
            selected = slot;
            sync();
        }

        private void sync() {
            if (selected == cached) return;
            cached = selected;
            server = selected;
            packets.add("SLOT " + selected);
        }

        private void send(String action) {
            assertTrue(ActionSwap.active());
            assertEquals(selected, server);
            packets.add(action + " " + server);
        }
    }
}
