package com.hunterbuddy.pet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PetUserReportsTest {
    @Test
    void thePetHasNoClimbingSurfaceOrState() {
        assertThrows(NoSuchFieldException.class, () -> PetSurfaces.class.getDeclaredField("climbing"));
        for (PetMotion.Travel travel : PetMotion.Travel.values()) assertNotEquals("CLIMB", travel.name());
    }

    @Test
    void stabilityRestartsOnlyWhenTheWorldChanges() {
        PetStability stability = new PetStability();
        Object world = new Object();
        assertFalse(stability.ready(0));
        stability.update(world, 0);
        assertFalse(stability.ready(14999));
        assertTrue(stability.ready(15000));
        stability.update(new Object(), 21000);
        assertFalse(stability.ready(35999));
        assertTrue(stability.ready(36000));
        stability.restart();
        assertFalse(stability.ready(36000));
        stability.adopt(world, 0);
        assertTrue(stability.ready(15000));
    }

    @Test
    void theWaveIsOnlyTheGreetingNeverARandomActivity() {
        for (long seed = 0; seed < 20; seed++) {
            PetBrain brain = new PetBrain(seed);
            brain.reset();
            brain.grounded = true;
            brain.worldStable = true;
            brain.update(0.05f, false, 1, 0, true);
            brain.greeted();
            assertEquals(PetState.WAVE, brain.state);
            for (int tick = 0; tick < 20 * 3600; tick++) {
                brain.update(0.05f, false, 1, 0, true);
                if (tick > 20 * 10) assertNotEquals(PetState.WAVE, brain.state);
            }
        }
    }

    @Test
    void theGreetingWaitsForAStableWorldThenSpeaks() {
        PetBrain brain = new PetBrain(5);
        brain.reset();
        brain.grounded = true;
        brain.worldStable = false;
        PetEvents.listen(true);
        try {
            for (int i = 0; i < 400; i++) brain.update(0.05f, false, 1, 0, true);
            assertEquals(0, PetEvents.drain() & (1L << PetEvent.GREETING.ordinal()));
            brain.worldStable = true;
            for (int i = 0; i < 400 && (PetEvents.drain() & (1L << PetEvent.GREETING.ordinal())) == 0; i++) {
                brain.update(0.05f, false, 1, 0, true);
                assertNotEquals(PetState.WAVE, brain.state);
            }
            brain.greeted();
            assertEquals(PetState.WAVE, brain.state);
        } finally {
            PetEvents.listen(false);
        }
    }

    @Test
    void thePetDoesNothingButWalkOrRestBeforeTheGreeting() {
        long greeting = 1L << PetEvent.GREETING.ordinal();
        PetEvents.listen(true);
        try {
            for (long seed = 0; seed < 20; seed++) {
                PetBrain brain = new PetBrain(seed);
                brain.reset();
                brain.grounded = true;
                brain.worldStable = false;
                PetEvents.drain();
                boolean greeted = false;
                for (int tick = 0; tick < 20 * 600 && !greeted; tick++) {
                    if (tick == 20 * 300) brain.worldStable = true;
                    brain.update(0.05f, false, 1, 0, true);
                    greeted = (PetEvents.drain() & greeting) != 0;
                    if (!greeted) assertTrue(brain.state == PetState.WALK || brain.state == PetState.REST, "seed " + seed + " " + brain.state);
                }
                assertTrue(greeted, "seed " + seed);
            }
        } finally {
            PetEvents.listen(false);
        }
    }

    @Test
    void theWaveStartsWithTheGreetingLineNeverAlone() {
        PetEvents.listen(true);
        try {
            PetBrain brain = new PetBrain(4);
            brain.reset();
            brain.grounded = true;
            brain.worldStable = true;
            PetEvents.drain();
            for (int tick = 0; tick < 20 * 20; tick++) {
                brain.update(0.05f, false, 1, 0, true);
                assertNotEquals(PetState.WAVE, brain.state);
            }
            assertNotEquals(0, PetEvents.drain() & (1L << PetEvent.GREETING.ordinal()));
            brain.greeted();
            assertEquals(PetState.WAVE, brain.state);
        } finally {
            PetEvents.listen(false);
        }
    }

    @Test
    void anUnspokenGreetingTimesOutWithoutAWave() {
        PetBrain brain = new PetBrain(4);
        brain.reset();
        brain.grounded = true;
        brain.worldStable = true;
        for (int tick = 0; tick < 20 * 40; tick++) {
            brain.update(0.05f, false, 1, 0, true);
            assertNotEquals(PetState.WAVE, brain.state);
        }
        brain.greeted();
        assertNotEquals(PetState.WAVE, brain.state);
    }

    @Test
    void theWaveImageHoldsFromTheGreetingLineThroughTheWholeWave() {
        PetResources.Rig rig = PetAnimationTest.rig(new PetResources.FullPose("wave", null, 100, 200, 50, 199, 0.5f, 50, 50, 0));
        PetBrain brain = new PetBrain(4);
        brain.reset();
        brain.grounded = true;
        brain.worldStable = true;
        PetMotion motion = new PetMotion(77);
        motion.width = 1280;
        motion.height = 720;
        motion.x = motion.previousX = 300;
        motion.y = motion.previousY = 650;
        motion.travel = PetMotion.Travel.GROUNDED;
        PetPose puppet = new PetPose();
        PetFullPose images = new PetFullPose();
        int tick = 0;
        for (; tick < 20 * 20 && !images.ready("wave"); tick++) {
            brain.update(0.05f, false, 1, 0, true);
            images.update(0.05f, rig, "wave", brain, motion, puppet);
            puppet.update(brain, rig, motion, 0.05f);
            assertNotEquals(PetState.WAVE, brain.state);
        }
        assertTrue(images.ready("wave"));
        assertTrue(tick >= 8);
        brain.greeted();
        assertEquals(PetState.WAVE, brain.state);
        for (int wave = 0; wave < 20 * 3; wave++) {
            brain.update(0.05f, false, 1, 0, true);
            images.update(0.05f, rig, wave < 20 ? "wave" : null, brain, motion, puppet);
            puppet.update(brain, rig, motion, 0.05f);
            assertEquals(PetState.WAVE, brain.state);
            assertTrue(images.ready("wave"));
        }
        for (int end = 0; end < 20 * 10 && images.blocksMotion(); end++) {
            brain.update(0.05f, false, 1, 0, true);
            images.update(0.05f, rig, null, brain, motion, puppet);
            puppet.update(brain, rig, motion, 0.05f);
        }
        assertFalse(images.blocksMotion());
        assertNotEquals(PetState.WAVE, brain.state);
    }

    @Test
    void onlyACloseOrRecentlyAlarmingPlayerIsADanger() {
        assertFalse(PetMonitor.closeEnough(100.0 * 100.0, 32));
        assertFalse(PetMonitor.closeEnough(249.0 * 249.0, 32));
        assertTrue(PetMonitor.closeEnough(31.0 * 31.0, 32));
        PetBrain brain = new PetBrain(3);
        brain.reset();
        assertFalse(brain.danger());
        brain.react(PetEvent.PLAYER_CLOSE, false, true);
        assertTrue(brain.danger());
        for (int tick = 0; tick < 20 * 16; tick++) brain.update(0.05f, false, 1, 0, true);
        assertFalse(brain.danger());
    }

    @Test
    void anIdlePetStaysAsleepUntilRealInput() {
        PetBrain brain = new PetBrain(9);
        brain.reset();
        brain.grounded = true;
        brain.worldStable = true;
        brain.update(0.05f, false, 1, 0, true);
        brain.greeted();
        assertEquals(PetState.WAVE, brain.state);
        brain.minimumSleep = 30;
        brain.maximumSleep = 30;
        brain.state = PetState.SLEEP;
        brain.age = 0;
        for (int tick = 0; tick < 20 * 600; tick++) {
            brain.update(0.05f, true, 1, 0, true);
            assertEquals(PetState.SLEEP, brain.state);
        }
        for (int tick = 0; tick < 20 * 20 && brain.state == PetState.SLEEP; tick++) brain.update(0.05f, false, 1, 0, true);
        assertEquals(PetState.RISE, brain.state);
    }

    private static PetMotion groundedMotion() {
        PetMotion motion = new PetMotion(77);
        motion.width = 1280;
        motion.height = 720;
        motion.x = motion.previousX = 300;
        motion.y = motion.previousY = 650;
        motion.travel = PetMotion.Travel.GROUNDED;
        return motion;
    }

    private static PetResources.FullPose image(String name) {
        return new PetResources.FullPose(name, null, 100, 200, 50, 199, 0.5f, 50, 50, -20);
    }

    private static void showAndDismiss(PetFullPose poses, PetResources.Rig rig, PetBrain brain, PetMotion motion, PetPose puppet) {
        for (int i = 0; i < 80; i++) {
            poses.update(0.05f, rig, "moon", brain, motion, puppet);
            puppet.update(brain, rig, motion, 0.05f);
        }
        assertTrue(poses.ready("moon"));
        for (int i = 0; i < 30; i++) poses.update(0.05f, rig, null, brain, motion, puppet);
        assertFalse(poses.blocksMotion());
    }

    @Test
    void anImageRequestedDuringTheCooldownNeverFreezesTheBrainOrTheMotion() {
        PetResources.Rig rig = PetAnimationTest.rig(image("moon"));
        PetBrain brain = new PetBrain(3);
        PetMotion motion = groundedMotion();
        PetPose puppet = new PetPose();
        PetFullPose poses = new PetFullPose();
        showAndDismiss(poses, rig, brain, motion, puppet);
        boolean shown = false;
        for (int i = 0; i < 400; i++) {
            poses.update(0.05f, rig, "moon", brain, motion, puppet);
            puppet.update(brain, rig, motion, 0.05f);
            if (poses.ready("moon")) {
                shown = true;
                break;
            }
            if (i < 100) {
                assertFalse(poses.blocksMotion());
                assertFalse(brain.preparingPose);
            }
        }
        assertTrue(shown);
    }

    @Test
    void theSleepImageDoesNotWaitForTheCooldown() {
        PetResources.Rig rig = PetAnimationTest.rig(image("moon"), image("sleep"));
        PetBrain brain = new PetBrain(3);
        PetMotion motion = groundedMotion();
        PetPose puppet = new PetPose();
        PetFullPose poses = new PetFullPose();
        showAndDismiss(poses, rig, brain, motion, puppet);
        brain.state = PetState.LIE_DOWN;
        brain.age = 0;
        brain.duration = 3;
        for (int i = 0; i < 6; i++) poses.update(0.05f, rig, null, brain, motion, puppet);
        assertTrue(poses.ready("sleep"));
    }

    @Test
    void aWakeUpEventRaisesASleepingPetAndIsSpoken() {
        PetBrain brain = new PetBrain(8);
        brain.reset();
        brain.state = PetState.SLEEP;
        brain.age = 5;
        brain.duration = 100;
        assertTrue(brain.asleep());
        assertTrue(new PetDialogue().offer(PetEvent.WAKE_UP, 0, true));
        brain.react(PetEvent.WAKE_UP, true, true);
        brain.update(0.05f, false, 1, 0, true);
        assertEquals(PetState.RISE, brain.state);
        for (int tick = 0; tick < 20 * 10 && brain.state == PetState.RISE; tick++) brain.update(0.05f, false, 1, 0, true);
        assertEquals(PetState.REST, brain.state);
    }

    @Test
    void aSleepingPetIgnoresOrdinaryReactionsButARestingOneIsNotAsleep() {
        PetBrain brain = new PetBrain(8);
        brain.reset();
        brain.state = PetState.RISE;
        assertFalse(brain.asleep());
        assertTrue(brain.resting());
        assertFalse(new PetDialogue().offer(PetEvent.ITEM_SPOTTED, 0, true));
    }

    @Test
    void theSecondPlannedSeatedDrinkIsNotBlockedByTheFirst() {
        int doubles = 0;
        for (long seed = 0; seed < 60; seed++) {
            PetBrain brain = new PetBrain(seed);
            brain.reset();
            brain.state = PetState.SIT;
            brain.duration = 75;
            brain.elevated = brain.atEdge = brain.grounded = true;
            brain.worldStable = true;
            java.util.List<String> lines = new java.util.ArrayList<>();
            brain.announce = lines::add;
            for (int tick = 0; tick < 20 * 74 && brain.state == PetState.SIT; tick++) brain.update(0.05f, false, 1, 0, true);
            assertFalse(lines.contains("[pet] seated drink blocked: drink cooldown"));
            if (lines.stream().filter("[pet] seated drink"::equals).count() == 2) doubles++;
        }
        assertTrue(doubles > 0);
    }

    @Test
    void aPetUnderASeatableSurfaceHopsOntoItInsteadOfStandingIdleInSit() {
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        surfaces.enabled = surfaces.bottom = true;
        surfaces.begin(1280, 720);
        surfaces.add(200, 600, 620, 3);
        PetMotion motion = new PetMotion(5);
        PetBrain brain = new PetBrain(5);
        brain.state = PetState.SIT;
        motion.update(brain, 0.05f, surfaces, 100, 67, false);
        motion.x = 300;
        motion.y = 720;
        motion.travel = PetMotion.Travel.GROUNDED;
        boolean perched = false;
        for (int i = 0; i < 100 && !perched; i++) {
            motion.update(brain, 0.05f, surfaces, 100, 67, false);
            perched = motion.elevated;
        }
        assertTrue(perched);
    }

    @Test
    void aDanceCrossFadeInterruptedByTheExitResolvesInsteadOfFreezing() {
        PetResources.FullPose first = image("dance_1");
        PetResources.FullPose second = image("dance_2");
        PetDanceSequence dance = new PetDanceSequence(7);
        PetResources.FullPose[] frames = {first, second};
        dance.start(frames, new int[2], new int[2], first);
        for (int i = 0; i < 40 && dance.next == null; i++) dance.update(0.05f);
        assertNotNull(dance.next);
        PetResources.FullPose target = dance.next;
        for (int i = 0; i < 4; i++) dance.update(0.05f);
        for (int i = 0; i < 60; i++) dance.finish(0.05f);
        assertNull(dance.next);
        assertEquals(0, dance.mix);
        assertSame(target, dance.current);
    }

    private static void enter(PetBrain brain, PetState state) throws ReflectiveOperationException {
        var method = PetBrain.class.getDeclaredMethod("enter", PetState.class, float.class, float.class);
        method.setAccessible(true);
        method.invoke(brain, state, 6f, 6f);
    }

    @Test
    void anImageDrinkBlockedByTheCooldownIsPlayedEntirelyByThePuppetAndTheNextOneIsTheImage() throws ReflectiveOperationException {
        PetResources.Rig rig = PetAnimationTest.rig(image("moon"), image("drink"));
        PetBrain brain = new PetBrain(3);
        brain.reset();
        PetMotion motion = groundedMotion();
        PetPose puppet = new PetPose();
        PetFullPose poses = new PetFullPose();
        showAndDismiss(poses, rig, brain, motion, puppet);
        enter(brain, PetState.DRINK);
        assertTrue(brain.imageDrink);
        for (int i = 0; i < 400; i++) {
            poses.update(0.05f, rig, null, brain, motion, puppet);
            assertFalse(brain.imageDrink);
            assertEquals(0, poses.blend);
            assertFalse(poses.ready("drink"));
            assertFalse(brain.preparingPose);
        }
        enter(brain, PetState.DRINK);
        assertTrue(brain.imageDrink);
    }

    @Test
    void anImageDanceBlockedByTheCooldownIsPlayedEntirelyByThePuppetAndTheNextOneIsTheImage() throws ReflectiveOperationException {
        PetResources.Rig rig = PetAnimationTest.rig(image("moon"), image("dance_1"), image("dance_2"));
        PetBrain brain = new PetBrain(3);
        brain.reset();
        brain.danceImageCount = 2;
        PetMotion motion = groundedMotion();
        PetPose puppet = new PetPose();
        PetFullPose poses = new PetFullPose();
        showAndDismiss(poses, rig, brain, motion, puppet);
        enter(brain, PetState.DANCE);
        assertFalse(brain.imageDance);
        enter(brain, PetState.DANCE);
        assertTrue(brain.imageDance);
        for (int i = 0; i < 400; i++) {
            poses.update(0.05f, rig, null, brain, motion, puppet);
            assertFalse(brain.imageDance);
            assertEquals(0, poses.blend);
            assertFalse(brain.preparingPose);
        }
        enter(brain, PetState.DANCE);
        assertTrue(brain.imageDance);
    }
}
