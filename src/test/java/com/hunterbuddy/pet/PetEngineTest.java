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
    void sleepUsesPostureTransitionsAndDoesNotRestartAfterWaking() {
        PetEvents.listen(true);
        PetBrain brain = new PetBrain(8);
        brain.state = PetState.SETTLE;
        brain.duration = 2;
        brain.minimumSleep = 30;
        brain.maximumSleep = 30;
        int sleepLines = 0;
        int wakes = 0;
        float sleeping = 0;
        boolean rose = false;
        for (int i = 0; i < 1000; i++) {
            PetState previous = brain.state;
            brain.update(0.05f, true, 1, 0, false);
            if (previous == PetState.SLEEP) sleeping += 0.05f;
            long pending = PetEvents.drain();
            if ((pending & (1L << PetEvent.SLEEP.ordinal())) != 0) sleepLines++;
            if ((pending & (1L << PetEvent.WAKE_UP.ordinal())) != 0) wakes++;
            if (brain.state == PetState.RISE) rose = true;
            if (rose) assertFalse(brain.state == PetState.SETTLE || brain.state == PetState.SLEEP);
        }
        assertTrue(sleeping >= 29.99f);
        assertTrue(sleepLines <= 1);
        assertEquals(1, wakes);
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
    void feetReachTheExactScreenBottomAndFallWhenSupportMoves() {
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        surfaces.enabled = true;
        surfaces.bottom = true;
        surfaces.begin(800, 600);
        PetBrain brain = new PetBrain();
        brain.state = PetState.REST;
        PetMotion motion = new PetMotion();
        motion.reset();
        for (int i = 0; i < 100; i++) motion.update(brain, 0.05f, surfaces, 80, true, false);
        assertEquals(600, motion.y);
        assertEquals(PetMotion.Travel.GROUNDED, motion.travel);
        surfaces.add(0, 200, 400, 3);
        motion.y = 400;
        motion.travel = PetMotion.Travel.GROUNDED;
        surfaces.begin(800, 600);
        surfaces.add(200, 300, 400, 3);
        motion.update(brain, 0.05f, surfaces, 80, true, false);
        assertEquals(PetMotion.Travel.FALL, motion.travel);
        for (int i = 0; i < 100; i++) motion.update(brain, 0.05f, surfaces, 80, true, false);
        assertEquals(600, motion.y);
        assertEquals(PetMotion.Travel.GROUNDED, motion.travel);
    }

    @Test
    void fallingLandsOnTheFirstVisiblePlatformBelowTheFeet() {
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        surfaces.enabled = true;
        surfaces.bottom = true;
        surfaces.begin(800, 600);
        surfaces.add(0, 300, 450, 3);
        PetBrain brain = new PetBrain();
        brain.state = PetState.REST;
        PetMotion motion = new PetMotion();
        motion.update(brain, 0.05f, surfaces, 80, true, false);
        motion.y = 300;
        for (int i = 0; i < 100; i++) motion.update(brain, 0.05f, surfaces, 80, true, false);
        assertEquals(450, motion.y);
        assertEquals(PetMotion.Travel.GROUNDED, motion.travel);
        assertEquals(-1, surfaces.below(400, 300, 500));
    }

    @Test
    void visibleHotbarCutsTheFloorIntoTwoNonOverlappingSpans() {
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        surfaces.enabled = true;
        surfaces.bottom = true;
        surfaces.begin(800, 600);
        surfaces.excludeFloor(200, 600);
        assertTrue(surfaces.support(100, 600) >= 0);
        assertTrue(surfaces.support(700, 600) >= 0);
        assertEquals(-1, surfaces.support(400, 600));
        assertEquals(-1, surfaces.below(400, 500, 700));
        surfaces.begin(800, 600);
        assertTrue(surfaces.support(400, 600) >= 0);
    }

    @Test
    void ambientActivitiesDoNotRepeatAndRespectContext() {
        PetBrain brain = new PetBrain();
        brain.reset();
        for (int i = 0; i < 1000; i++) {
            PetState previous = brain.state;
            brain.age = brain.duration + 1;
            brain.update(0.05f, false, 0, 0, false);
            assertFalse(previous == brain.state);
            assertFalse(brain.state == PetState.SIT || brain.state == PetState.STAGGER || brain.state == PetState.STUMBLE || brain.state == PetState.THROW);
        }
    }

    @Test
    void fallingIntoTheHotbarGapRecoversToAVisibleSupport() {
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        surfaces.enabled = true;
        surfaces.bottom = true;
        surfaces.begin(800, 600);
        surfaces.excludeFloor(200, 600);
        PetBrain brain = new PetBrain();
        brain.state = PetState.HIDE;
        PetMotion motion = new PetMotion();
        motion.update(brain, 0.05f, surfaces, 80, true, false);
        motion.x = 400;
        motion.y = 590;
        boolean recovered = false;
        for (int i = 0; i < 200; i++) {
            motion.update(brain, 0.05f, surfaces, 80, true, true);
            assertTrue(motion.y <= 600);
            recovered |= motion.travel == PetMotion.Travel.GROUNDED;
        }
        assertTrue(recovered);
    }

    @Test
    void hiddenEyesAreKeptInsideTheViewportInBothDirections() {
        PetMotion motion = new PetMotion();
        motion.width = 800;
        motion.height = 600;
        for (int direction = -1; direction <= 1; direction += 2) {
            motion.direction = direction;
            motion.x = direction == -1 ? 0 : 800;
            motion.y = 650;
            motion.previousX = motion.x;
            motion.previousY = motion.y;
            float eyeX = motion.x + direction * 30;
            float eyeY = 620;
            float oldX = motion.x;
            float oldY = motion.y;
            PetVisibility.keepEyesVisible(motion, eyeX, eyeY, 80);
            assertTrue(eyeX + motion.x - oldX > 0 && eyeX + motion.x - oldX < 800);
            assertTrue(eyeY + motion.y - oldY > 0 && eyeY + motion.y - oldY < 600);
            assertEquals(motion.x, motion.previousX);
            assertEquals(motion.y, motion.previousY);
        }
    }

    @Test
    void surfaceAndMotionUpdatesAllocateNothingAfterWarmup() {
        var allocations = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        surfaces.enabled = true;
        surfaces.bottom = true;
        PetBrain brain = new PetBrain();
        PetMotion motion = new PetMotion();
        simulate(surfaces, brain, motion, 100000);
        long thread = Thread.currentThread().threadId();
        long minimum = Long.MAX_VALUE;
        for (int sample = 0; sample < 5; sample++) {
            long before = allocations.getThreadAllocatedBytes(thread);
            simulate(surfaces, brain, motion, 10000);
            minimum = Math.min(minimum, allocations.getThreadAllocatedBytes(thread) - before);
        }
        assertEquals(0, minimum);
    }

    private static void simulate(PetSurfaces surfaces, PetBrain brain, PetMotion motion, int iterations) {
        for (int i = 0; i < iterations; i++) {
            surfaces.begin(800, 600);
            surfaces.excludeFloor(200, 600);
            surfaces.add(200, 600, 550, 1);
            brain.update(0.05f, false, 1, 0, true);
            motion.update(brain, 0.05f, surfaces, 80, true, false);
        }
    }

    @Test
    void ambientEventsCannotReplaceAnAlertPose() {
        PetBrain brain = new PetBrain();
        brain.react(PetEvent.TOTEM_POP, false);
        assertEquals(PetState.PANIC, brain.state);
        brain.react(PetEvent.IDLE, false);
        assertEquals(PetState.PANIC, brain.state);
        brain.react(PetEvent.PLAYER_ARRIVE, true);
        assertEquals(PetState.PANIC, brain.state);
        assertFalse(PetEvent.IDLE.important());
        assertTrue(PetEvent.PLAYER_CLOSE.important());
    }
}
