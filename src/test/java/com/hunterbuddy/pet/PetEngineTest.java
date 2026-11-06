package com.hunterbuddy.pet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PetEngineTest {
    @AfterEach
    void stopListening() {
        PetEvents.listen(false);
    }

    @Test
    void busDropsInactiveEventsAndDrainsOnlyOnce() {
        PetEvents.listen(false);
        PetEvents.publish(PetEvent.PLAYER_DEATH);
        assertEquals(0, PetEvents.drain());
        PetEvents.listen(true);
        PetEvents.publish(PetEvent.PLAYER_DEATH);
        PetEvents.publish(PetEvent.IDLE);
        long expected = (1L << PetEvent.PLAYER_DEATH.ordinal()) | (1L << PetEvent.IDLE.ordinal());
        assertEquals(expected, PetEvents.drain());
        assertEquals(0, PetEvents.drain());
    }

    @Test
    void busDoesNotLoseIndependentConcurrentEvents() throws InterruptedException {
        PetEvents.listen(true);
        Thread first = new Thread(() -> {
            for (int i = 0; i < 10000; i++) PetEvents.publish(PetEvent.TOTEM_POP);
        });
        Thread second = new Thread(() -> {
            for (int i = 0; i < 10000; i++) PetEvents.publish(PetEvent.STASH_FOUND);
        });
        first.start();
        second.start();
        first.join();
        second.join();
        long expected = (1L << PetEvent.TOTEM_POP.ordinal()) | (1L << PetEvent.STASH_FOUND.ordinal());
        assertEquals(expected, PetEvents.drain());
        assertEquals(10000, PetEvents.count(PetEvent.TOTEM_POP));
        assertEquals(10000, PetEvents.count(PetEvent.STASH_FOUND));
    }

    @Test
    void sleepingAndWakingEmitExactlyOneTransition() {
        PetEvents.listen(true);
        PetBrain brain = new PetBrain();
        brain.update(0.05f, true, 1, 1, true);
        assertEquals(PetState.SLEEP, brain.state);
        assertEquals(1L << PetEvent.SLEEP.ordinal(), PetEvents.drain());
        brain.update(0.05f, true, 1, 1, true);
        assertEquals(0, PetEvents.drain());
        brain.update(0.05f, false, 1, 1, true);
        assertEquals(PetState.REST, brain.state);
        assertEquals(1L << PetEvent.WAKE_UP.ordinal(), PetEvents.drain());
    }

    @Test
    void drunkennessDecaysAndRespectsTheConfiguredMaximum() {
        PetBrain brain = new PetBrain();
        brain.drunkenness = 1;
        brain.update(0.05f, true, 0.5, 0, false);
        assertTrue(brain.drunkenness <= 0.5f);
        for (int i = 0; i < 2000; i++) brain.update(0.05f, true, 0.5, 0, false);
        assertEquals(0, brain.drunkenness);
    }

    @Test
    void walkingTraversesBothSidesAndTheBottomWithoutEnteringTheHotbar() {
        PetBrain brain = new PetBrain();
        PetMotion motion = new PetMotion();
        motion.reset();
        boolean left = false;
        boolean right = false;
        boolean bottom = false;
        for (int i = 0; i < 4000; i++) {
            brain.age = i * 0.05f;
            motion.update(brain, 0.05f, 800, 600, 80, 2);
            assertTrue(Float.isFinite(motion.x) && Float.isFinite(motion.y));
            assertTrue(motion.x >= 0 && motion.x <= 800);
            assertTrue(motion.y >= 80 && motion.y <= 544);
            left |= motion.x < 40;
            right |= motion.x > 760;
            bottom |= motion.x > 300 && motion.x < 500 && motion.y == 544;
        }
        assertTrue(left && right && bottom);
    }

    @Test
    void ambientEventsCannotReplaceAnAlertPose() {
        PetBrain brain = new PetBrain();
        brain.react(PetEvent.TOTEM_POP, false);
        assertEquals(PetState.PANIC, brain.state);
        brain.react(PetEvent.IDLE, false);
        assertEquals(PetState.PANIC, brain.state);
        brain.react(PetEvent.PLAYER_ARRIVE, true);
        assertEquals(PetState.HIDE, brain.state);
        assertFalse(PetEvent.IDLE.important());
        assertTrue(PetEvent.PLAYER_CLOSE.important());
    }
}
