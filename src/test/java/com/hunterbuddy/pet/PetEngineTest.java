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
            brain.update(0.05f, i < 700, 1, 0, false);
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
        PetBrain brain = new PetBrain(1001);
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
        PetBrain brain = new PetBrain(1002);
        brain.state = PetState.REST;
        PetMotion motion = new PetMotion(77);
        motion.reset();
        for (int i = 0; i < 100; i++) motion.update(brain, 0.05f, surfaces, 80, 53, false);
        assertEquals(600, motion.y);
        assertEquals(PetMotion.Travel.GROUNDED, motion.travel);
        surfaces.add(0, 200, 400, 3);
        motion.y = 400;
        motion.travel = PetMotion.Travel.GROUNDED;
        surfaces.begin(800, 600);
        surfaces.add(200, 300, 400, 3);
        motion.update(brain, 0.05f, surfaces, 80, 53, false);
        assertEquals(PetMotion.Travel.FALL, motion.travel);
        for (int i = 0; i < 100; i++) motion.update(brain, 0.05f, surfaces, 80, 53, false);
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
        PetBrain brain = new PetBrain(1003);
        brain.state = PetState.REST;
        PetMotion motion = new PetMotion(77);
        motion.update(brain, 0.05f, surfaces, 80, 53, false);
        motion.x = 100;
        motion.y = 300;
        for (int i = 0; i < 100; i++) motion.update(brain, 0.05f, surfaces, 80, 53, false);
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
        PetBrain brain = new PetBrain(1004);
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
        PetBrain brain = new PetBrain(1005);
        brain.state = PetState.HIDE;
        PetMotion motion = new PetMotion(77);
        motion.update(brain, 0.05f, surfaces, 80, 53, false);
        motion.x = 400;
        motion.y = 590;
        boolean recovered = false;
        for (int i = 0; i < 200; i++) {
            motion.update(brain, 0.05f, surfaces, 80, 53, true);
            assertTrue(motion.y <= 600);
            recovered |= motion.travel == PetMotion.Travel.GROUNDED;
        }
        assertTrue(recovered);
    }

    @Test
    void hiddenEyesAreKeptInsideTheViewportInBothDirections() {
        PetMotion motion = new PetMotion(77);
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
        PetBrain brain = new PetBrain(1006);
        PetMotion motion = new PetMotion(77);
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
            motion.update(brain, 0.05f, surfaces, 80, 53, false);
        }
    }

    @Test
    void theWanderingPetSpendsAFairShareOfTimeInEveryScreenThird() {
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        surfaces.enabled = true;
        surfaces.bottom = true;
        for (long seed = 1; seed <= 4; seed++) {
            PetBrain brain = new PetBrain(seed);
            brain.reset();
            brain.worldStable = true;
            PetMotion motion = new PetMotion(seed);
            motion.reset();
            int[] thirds = new int[3];
            int hops = 0;
            PetMotion.Travel previous = PetMotion.Travel.GROUNDED;
            for (int tick = 0; tick < 20 * 60 * 30; tick++) {
                surfaces.begin(1280, 720);
                surfaces.excludeFloor(440, 840);
                surfaces.add(440, 840, 670, 1);
                surfaces.add(460, 820, 640, 2);
                surfaces.add(1020, 1280, 640, 3);
                brain.externalDanger = false;
                brain.grounded = motion.travel == PetMotion.Travel.GROUNDED;
                brain.elevated = motion.elevated;
                brain.highEdge = motion.highEdge;
                brain.atEdge = motion.atEdge;
                brain.seatReachable = motion.seatReachable;
                brain.seeking = motion.seeking;
                brain.roomToLie = motion.roomToLie;
                brain.surfaceKind = motion.surfaceKind;
                brain.update(0.05f, false, 1, 0, true);
                motion.update(brain, 0.05f, surfaces, 80, 53, brain.resting());
                assertTrue(motion.x >= 0 && motion.x <= 1280 && motion.y >= 0 && motion.y <= 720);
                if (motion.travel == PetMotion.Travel.JUMP && previous != PetMotion.Travel.JUMP) hops++;
                previous = motion.travel;
                if (tick > 20 * 60 && brain.state == PetState.WALK) thirds[Math.min(2, (int) (motion.x * 3 / 1280))]++;
            }
            int total = thirds[0] + thirds[1] + thirds[2];
            assertTrue(hops < 90, "hops " + hops);
            for (int third : thirds) assertTrue(third > total * 0.15, "seed " + seed + " thirds " + java.util.Arrays.toString(thirds));
        }
    }

    @Test
    void aLongDanceIsCutShortByIdleSleepAndDanceDurationsStayInRange() {
        PetBrain brain = new PetBrain(9);
        brain.reset();
        brain.state = PetState.DANCE;
        brain.duration = 100;
        brain.age = 0;
        brain.update(0.05f, false, 1, 0, true);
        assertEquals(PetState.DANCE, brain.state);
        brain.update(0.05f, true, 1, 0, true);
        assertTrue(brain.state != PetState.DANCE);
        for (long seed = 0; seed < 40; seed++) {
            PetBrain other = new PetBrain(seed);
            other.reset();
            other.grounded = true;
            for (int tick = 0; tick < 20 * 600; tick++) {
                other.update(0.05f, false, 1, 0, true);
                if (other.state == PetState.DANCE && other.age == 0) assertTrue(other.duration >= 15 && other.duration <= 120);
            }
        }
    }

    @Test
    void longRunsSitSeveralTimesHopRarelyAndKeepCustomAnimationsOccasional() {
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        surfaces.enabled = true;
        surfaces.bottom = true;
        float[] zero = new float[PetAnimation.JOINTS.length];
        PetAnimation[] animations = new PetAnimation[13];
        for (int i = 0; i < animations.length; i++) {
            animations[i] = new PetAnimation("custom_" + i, 3, 1, 0.14f, false, new PetAnimation.Key[]{
                new PetAnimation.Key(0, true, zero), new PetAnimation.Key(0.5f, true, zero)});
        }
        for (long seed = 1; seed <= 4; seed++) {
            PetBrain brain = new PetBrain(seed);
            brain.reset();
            brain.worldStable = true;
            brain.animations = animations;
            PetMotion motion = new PetMotion(seed);
            motion.reset();
            int sits = 0;
            int hops = 0;
            int customs = 0;
            PetState previousState = brain.state;
            PetMotion.Travel previousTravel = PetMotion.Travel.GROUNDED;
            for (int tick = 0; tick < 20 * 60 * 30; tick++) {
                surfaces.begin(1280, 720);
                surfaces.excludeFloor(440, 840);
                surfaces.add(440, 840, 670, 1);
                surfaces.add(460, 820, 640, 2);
                surfaces.add(0, 160, 640, 3);
                surfaces.add(1100, 1280, 640, 3);
                brain.grounded = motion.travel == PetMotion.Travel.GROUNDED;
                brain.elevated = motion.elevated;
                brain.highEdge = motion.highEdge;
                brain.atEdge = motion.atEdge;
                brain.seatReachable = motion.seatReachable;
                brain.seeking = motion.seeking;
                brain.roomToLie = motion.roomToLie;
                brain.surfaceKind = motion.surfaceKind;
                brain.update(0.05f, false, 1, 0, true);
                motion.update(brain, 0.05f, surfaces, 80, 53, brain.resting());
                if (brain.state != previousState) {
                    if (brain.state == PetState.SIT) sits++;
                    if (brain.state == PetState.CUSTOM) customs++;
                }
                if (motion.travel == PetMotion.Travel.JUMP && previousTravel != PetMotion.Travel.JUMP) hops++;
                previousState = brain.state;
                previousTravel = motion.travel;
            }
            assertTrue(sits >= 9, "sits " + sits);
            assertTrue(hops <= 45, "hops " + hops);
            assertTrue(customs <= 30, "customs " + customs);
        }
    }

    @Test
    void ambientEventsCannotReplaceAnAlertPose() {
        PetBrain brain = new PetBrain(1007);
        brain.react(PetEvent.TOTEM_POP, false, true);
        assertEquals(PetState.PANIC, brain.state);
        brain.react(PetEvent.IDLE, false, true);
        assertEquals(PetState.PANIC, brain.state);
        brain.react(PetEvent.PLAYER_ARRIVE, true, true);
        assertEquals(PetState.PANIC, brain.state);
        assertFalse(PetEvent.IDLE.important());
        assertTrue(PetEvent.PLAYER_CLOSE.important());
    }
}
