package com.hunterbuddy.pet;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PetAnimationTest {
    private static PetResources.Part part(int index, float x, float y, float px, float py, int w, int h) {
        return new PetResources.Part(index, null, x, y, px, py, w, h, new float[]{0, 0, w, 0, w, h, 0, h});
    }

    private static PetResources.Rig rig(PetResources.FullPose... images) {
        PetResources.Part[] parts = {
            part(4, 86, 240, 106, 246, 38, 114), part(5, 130, 240, 148, 246, 38, 114),
            part(0, 80, 0, 120, 130, 80, 130), part(2, 0, 100, 84, 180, 92, 110),
            part(3, 170, 105, 170, 168, 75, 125), part(1, 86, 128, 128, 246, 80, 118)
        };
        PetResources.FullPose[] frames = PetResources.danceFrames(images);
        return new PetResources.Rig(240, 360, parts, new float[]{12, 148, 224, 110, 138, 112, 120, 84, 128, 354},
            null, 36, 90, 18, 0, images, frames, new int[frames.length], new int[frames.length]);
    }

    private static PetMotion motion() {
        PetMotion motion = new PetMotion();
        motion.width = 1280;
        motion.height = 720;
        motion.x = motion.previousX = 300;
        motion.y = motion.previousY = 650;
        motion.travel = PetMotion.Travel.GROUNDED;
        return motion;
    }

    @Test
    void thirtyMinutesKeepSleepDurationsAnchorsAndHandsSafe() {
        PetResources.Rig rig = rig();
        PetBrain brain = new PetBrain(41827);
        PetMotion motion = motion();
        PetPose pose = new PetPose();
        PetSkeleton skeleton = new PetSkeleton();
        Matrix3x2f inverse = new Matrix3x2f();
        Vector2f local = new Vector2f();
        PetEvents.listen(true);
        PetState last = null;
        int repetitions = 0;
        int sleeps = 0;
        int sits = 0;
        int sleepLines = 0;
        int sleepTicks = 0;
        try {
            for (int tick = 0; tick < 36000; tick++) {
                brain.elevated = tick % 3600 >= 2400;
                brain.highEdge = brain.elevated;
                brain.surfaceKind = brain.elevated ? 3 : 0;
                motion.highEdge = brain.highEdge;
                brain.atEdge = motion.atEdge = brain.highEdge;
                brain.roomToLie = !brain.elevated;
                brain.night = tick % 12000 < 8000;
                brain.externalDanger = tick % 7000 >= 6800;
                brain.canDrink = pose.drink.available;
                if (tick % 2400 == 0) brain.spoke();
                PetState previous = brain.state;
                float previousAge = brain.age;
                brain.update(0.05f, tick % 3600 < 2400, 1, 0, true);
                if (previous == PetState.SLEEP) sleepTicks++;
                if (previous == PetState.SLEEP && brain.state != previous) {
                    assertTrue(sleepTicks >= 600, "Sleep ended before thirty seconds");
                    assertTrue(sleepLines <= 1, "Repeated sleep dialogue");
                    sleeps++;
                }
                if (brain.state != previous || brain.age < previousAge) {
                    repetitions = brain.state == last ? repetitions + 1 : 1;
                    last = brain.state;
                    assertTrue(repetitions <= 2);
                    if (brain.state == PetState.SLEEP) { sleepTicks = 0; sleepLines = 0; }
                    if (brain.state == PetState.SIT) sits++;
                }
                long pending = PetEvents.drain();
                if ((pending & (1L << PetEvent.SLEEP.ordinal())) != 0) sleepLines++;
                pose.update(brain, rig, motion, 0.05f);
                assertTrue(Math.abs(pose.angles[2]) <= 0.601f);
                if (brain.state != PetState.DRINK && Math.abs(pose.angles[3]) < 0.2f) assertTrue(Math.abs(pose.angles[3]) <= 0.201f);
                assertEquals(2, PetSkeleton.layer(2));
                assertEquals(3, PetSkeleton.layer(3));
                assertEquals(1, PetSkeleton.layer(4));
                assertEquals(0, PetSkeleton.layer(5));
                motion.direction = tick % 400 < 200 ? 1 : -1;
                skeleton.update(rig, motion, pose, 100, 1);
                for (int anchor = 0; anchor < 4; anchor++) {
                    int owner = anchor == 0 ? 2 : anchor == 1 ? 3 : 0;
                    skeleton.anchor(rig, anchor);
                    skeleton.matrices[owner].invert(inverse);
                    inverse.transformPosition(skeleton.anchorX, skeleton.anchorY, local);
                    for (PetResources.Part part : rig.parts()) {
                        if (part.index() != owner) continue;
                        assertTrue(local.x >= part.x() - 0.01f && local.x <= part.x() + part.width() + 0.01f);
                        assertTrue(local.y >= part.y() - 0.01f && local.y <= part.y() + part.height() + 0.01f);
                    }
                }
                if (brain.state != PetState.DRINK && !brain.resting() && pose.recline < 0.01f && Math.abs(pose.angles[3]) < 0.2f) {
                    skeleton.matrices[1].invert(inverse);
                    skeleton.anchor(rig, 0);
                    inverse.transformPosition(skeleton.anchorX, skeleton.anchorY, local);
                    assertTrue(local.x < 86, "Left hand crossed the torso");
                    skeleton.anchor(rig, 1);
                    inverse.transformPosition(skeleton.anchorX, skeleton.anchorY, local);
                    assertTrue(local.x > 166, "Right hand crossed the torso");
                }
            }
        } finally {
            PetEvents.listen(false);
        }
        System.out.printf("Simulated ticks: 36000; completed sleeps: %d; seated activities: %d%n", sleeps, sits);
        assertTrue(sleeps >= 2, "No sustained sleeps in thirty minutes");
        assertTrue(sits >= 3, "Elevated surfaces did not encourage sitting");
    }

    @Test
    void drinkingMeetsTheMouthExactlyInBothDirections() {
        PetResources.Rig rig = rig();
        PetPose pose = new PetPose();
        PetBrain brain = new PetBrain(3);
        PetMotion motion = motion();
        PetSkeleton skeleton = new PetSkeleton();
        brain.state = PetState.DRINK;
        brain.duration = 6;
        for (int i = 0; i < 72; i++) {
            brain.age = i * 0.05f;
            pose.update(brain, rig, motion, 0.05f);
        }
        assertTrue(pose.drink.available);
        assertTrue(pose.angles[0] < -0.25f);
        for (int direction = -1; direction <= 1; direction += 2) {
            motion.direction = direction;
            skeleton.update(rig, motion, pose, 100, 1);
            skeleton.anchor(rig, 1);
            float x = skeleton.anchorX;
            float y = skeleton.anchorY;
            skeleton.anchor(rig, 2);
            assertEquals(x, skeleton.anchorX, 0.001f);
            assertEquals(y, skeleton.anchorY, 0.001f);
        }
    }

    @Test
    void optionalImagesWaitForCalmTurnAndCrossFadeWithoutJumps() {
        PetResources.FullPose image = new PetResources.FullPose("moon", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, -20);
        PetResources.FullPose first = new PetResources.FullPose("dance_1", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, -20);
        PetResources.FullPose second = new PetResources.FullPose("dance_2", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, -20);
        PetResources.Rig rig = rig(image, first, second);
        PetBrain brain = new PetBrain(4);
        PetMotion motion = motion();
        PetPose puppet = new PetPose();
        PetFullPose poses = new PetFullPose();
        brain.state = PetState.DANCE;
        brain.imageDance = true;
        brain.duration = 10;
        for (int i = 0; i < 20; i++) { brain.age += 0.05f; puppet.update(brain, rig, motion, 0.05f); }
        int direction = motion.direction;
        boolean firstVisible = false;
        for (int tick = 0; tick < 80; tick++) {
            poses.update(0.05f, rig, "moon", brain, motion, puppet, 0);
            puppet.update(brain, rig, motion, 0.05f);
            assertTrue(Math.abs(poses.blend - poses.previousBlend) <= 0.201f);
            if (poses.blend > 0 && !firstVisible) {
                assertTrue(tick >= 15);
                assertEquals(-direction, motion.direction);
                assertTrue(puppet.calm());
                firstVisible = true;
            }
        }
        assertTrue(poses.ready("moon"));
        boolean changed = false;
        boolean mixed = false;
        for (int tick = 0; tick < 300; tick++) {
            poses.update(0.05f, rig, null, brain, motion, puppet, 0);
            puppet.update(brain, rig, motion, 0.05f);
            assertTrue(Math.abs(poses.blend - poses.previousBlend) <= 0.201f);
            changed |= poses.current == first;
            mixed |= poses.current == first && poses.mix > 0.1f && poses.mix < 0.9f;
        }
        assertTrue(changed);
        assertTrue(mixed);
        assertTrue(poses.current == first || poses.current == second);
        assertNull(PetFullPose.find(rig, "missing"));
    }

    @Test
    void sittingApproachesAVisiblePlatformEdgeAndLeavesTheLegsDangling() {
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        surfaces.enabled = surfaces.bottom = true;
        surfaces.begin(1280, 720);
        surfaces.add(0, 220, 600, 3);
        PetMotion motion = new PetMotion();
        PetBrain brain = new PetBrain(14);
        brain.state = PetState.SIT;
        motion.update(brain, 0.05f, surfaces, 100, true, false);
        motion.x = 120;
        motion.y = 600;
        motion.travel = PetMotion.Travel.GROUNDED;
        for (int i = 0; i < 200; i++) motion.update(brain, 0.05f, surfaces, 100, true, false);
        assertTrue(motion.atEdge);
        assertTrue(motion.x >= 217 && motion.x <= 220);
        PetResources.Rig rig = rig();
        PetPose pose = new PetPose();
        for (int i = 0; i < 100; i++) pose.update(brain, rig, motion, 0.05f);
        PetSkeleton skeleton = new PetSkeleton();
        skeleton.update(rig, motion, pose, 100, 1);
        skeleton.point(4, 106, 354);
        assertTrue(skeleton.anchorY > motion.y + 20);
    }

    @Test
    void anOptionalSleepImageKeepsTheFullMinimumSleepDuration() {
        PetResources.FullPose image = new PetResources.FullPose("sleep", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, -20);
        PetResources.Rig rig = rig(image);
        PetBrain brain = new PetBrain(12);
        PetMotion motion = motion();
        PetPose puppet = new PetPose();
        PetFullPose poses = new PetFullPose();
        brain.state = PetState.SETTLE;
        brain.duration = 2;
        brain.minimumSleep = brain.maximumSleep = 30;
        int sleeping = 0;
        boolean completed = false;
        for (int i = 0; i < 1200; i++) {
            PetState previous = brain.state;
            brain.update(0.05f, true, 1, 0, false);
            poses.update(0.05f, rig, null, brain, motion, puppet, 0);
            puppet.update(brain, rig, motion, 0.05f);
            if (previous == PetState.SLEEP) sleeping++;
            if (brain.state == PetState.SLEEP && brain.age > 0.5f) assertTrue(poses.ready("sleep"));
            if (previous == PetState.SLEEP && brain.state != previous) { completed = true; break; }
        }
        assertTrue(completed);
        assertTrue(sleeping >= 600);
    }

    @Test
    void wholePosesNeverEnterInTheAirOrInterruptAReclinedSleep() {
        PetResources.FullPose image = new PetResources.FullPose("moon", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, -20);
        PetResources.Rig rig = rig(image);
        PetBrain brain = new PetBrain(10);
        PetMotion motion = motion();
        PetPose puppet = new PetPose();
        PetFullPose poses = new PetFullPose();
        motion.travel = PetMotion.Travel.JUMP;
        for (int i = 0; i < 100; i++) poses.update(0.05f, rig, "moon", brain, motion, puppet, 0);
        assertEquals(0, poses.blend);
        assertFalse(poses.blocksMotion());
        motion.travel = PetMotion.Travel.GROUNDED;
        brain.state = PetState.SLEEP;
        brain.age = 10;
        puppet.fold = puppet.recline = 1;
        for (int i = 0; i < 100; i++) poses.update(0.05f, rig, "moon", brain, motion, puppet, 0);
        assertEquals(0, poses.blend);
        assertFalse(brain.preparingPose);
    }

    @Test
    void animationAndImageTransitionsAllocateNothingAfterWarmup() {
        var allocations = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        PetResources.Rig rig = rig(new PetResources.FullPose("swagger", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, -20));
        PetBrain brain = new PetBrain(5);
        PetPose pose = new PetPose();
        PetMotion motion = motion();
        PetSkeleton skeleton = new PetSkeleton();
        PetFullPose images = new PetFullPose();
        animate(rig, brain, pose, motion, skeleton, images, 100000);
        long thread = Thread.currentThread().threadId();
        long minimum = Long.MAX_VALUE;
        for (int sample = 0; sample < 5; sample++) {
            long before = allocations.getThreadAllocatedBytes(thread);
            animate(rig, brain, pose, motion, skeleton, images, 10000);
            minimum = Math.min(minimum, allocations.getThreadAllocatedBytes(thread) - before);
        }
        assertEquals(0, minimum);
    }

    private static void animate(PetResources.Rig rig, PetBrain brain, PetPose pose, PetMotion motion,
                                PetSkeleton skeleton, PetFullPose images, int ticks) {
        for (int i = 0; i < ticks; i++) {
            brain.canDrink = pose.drink.available;
            brain.danceImageCount = rig.dances().length;
            brain.update(0.05f, true, 1, 0, true);
            images.update(0.05f, rig, null, brain, motion, pose, 1);
            pose.update(brain, rig, motion, 0.05f);
            skeleton.update(rig, motion, pose, 100, 1);
            skeleton.anchor(rig, i % 4);
        }
    }

    @Test
    void settlingAndRisingKeepBothLegsOnTheSurface() {
        PetResources.Rig rig = rig();
        PetBrain brain = new PetBrain(9);
        PetPose pose = new PetPose();
        PetMotion motion = motion();
        PetSkeleton skeleton = new PetSkeleton();
        PetState[] states = {PetState.SETTLE, PetState.LIE_DOWN, PetState.SLEEP, PetState.RISE};
        for (PetState state : states) {
            brain.state = state;
            brain.duration = state == PetState.SLEEP ? 30 : 4;
            for (int tick = 0; tick < 80; tick++) {
                brain.age = tick * brain.duration / 80;
                pose.update(brain, rig, motion, 0.05f);
                skeleton.update(rig, motion, pose, 100, 1);
                for (PetResources.Part part : rig.parts()) {
                    if (part.index() < 4) continue;
                    float bottom = -Float.MAX_VALUE;
                    for (int corner = 0; corner < 4; corner++) {
                        skeleton.point(part.index(), part.x() + ((corner & 1) == 0 ? 0 : part.width()),
                            part.y() + ((corner & 2) == 0 ? 0 : part.height()));
                        bottom = Math.max(bottom, skeleton.anchorY);
                    }
                    assertEquals(motion.y, bottom, 0.001f);
                }
            }
        }
    }
    @Test
    void imageScaleAndContactDoNotDependOnCanvasHeight() {
        PetResources.FullPose sit = new PetResources.FullPose("sit", null, 100, 200, 70, 130, 20, 100, 0.6f, 35, 45, -25);
        PetResources.FullPose sleep = new PetResources.FullPose("sleep", null, 300, 90, 150, 89, 20, 70, 0.5f, 55, 20, 0);
        PetImageTransform transform = new PetImageTransform();
        Vector2f point = new Vector2f();
        for (int direction = -1; direction <= 1; direction += 2) {
            for (PetResources.FullPose image : new PetResources.FullPose[]{sit, sleep}) {
                transform.update(image, 360, 90, 0, direction, 1, 300, 600);
                transform.matrix.transformPosition(image.feetX(), image.feetY(), point);
                assertEquals(300, point.x, 0.001f);
                assertEquals(600, point.y, 0.001f);
                assertEquals(300 + direction * (image.eyesX() - image.feetX()) * image.scale() * 90 / 360, transform.eyesX, 0.001f);
                assertEquals(600 + (image.eyesY() - image.feetY()) * image.scale() * 90 / 360, transform.eyesY, 0.001f);
                transform.matrix.transformPosition(image.feetX(), image.height(), point);
                if (image == sit) assertTrue(point.y > 610);
                assertEquals(image.scale() * 90 / 360, Math.abs(transform.matrix.m00()), 0.0001f);
            }
        }
    }

    @Test
    void everyNumberedDanceFrameIsDiscoveredAndShuffled() {
        PetResources.FullPose first = new PetResources.FullPose("dance_1", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, 0);
        PetResources.FullPose third = new PetResources.FullPose("dance_3", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, 0);
        PetResources.FullPose later = new PetResources.FullPose("dance_17", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, 0);
        PetResources.FullPose invalid = new PetResources.FullPose("dance_x", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, 0);
        PetResources.FullPose[] frames = PetResources.danceFrames(new PetResources.FullPose[]{first, invalid, third, later});
        assertEquals(3, frames.length);
        PetDanceSequence sequence = new PetDanceSequence(982);
        sequence.start(frames, new int[frames.length], new int[frames.length], first);
        PetResources.FullPose last = first;
        boolean sawThird = false;
        boolean sawLater = false;
        boolean blended = false;
        int changes = 0;
        for (int tick = 0; tick < 2000; tick++) {
            sequence.update(0.05f);
            if (sequence.current != last) { changes++; last = sequence.current; }
            if (sequence.next != null) assertNotSame(sequence.current, sequence.next);
            sawThird |= sequence.current == third;
            sawLater |= sequence.current == later;
            blended |= sequence.mix > 0 && sequence.mix < 1;
        }
        assertTrue(sawThird && sawLater && blended);
        assertTrue(changes > 120 && changes < 220);
    }

    @Test
    void brainAlternatesPuppetAndImageDancesAndVariesPuppetStyles() {
        PetBrain brain = new PetBrain(381);
        brain.danceImageCount = 3;
        brain.canDrink = true;
        boolean lastImage = false;
        int lastStyle = -1;
        int dances = 0;
        int styles = 0;
        for (int tick = 0; tick < 144000; tick++) {
            PetState previous = brain.state;
            brain.update(0.05f, false, 1, 0, true);
            if (brain.state != PetState.DANCE || previous == PetState.DANCE) continue;
            assertNotEquals(lastImage, brain.imageDance);
            lastImage = brain.imageDance;
            dances++;
            if (!brain.imageDance) {
                assertNotEquals(lastStyle, brain.danceStyle);
                lastStyle = brain.danceStyle;
                styles |= 1 << brain.danceStyle;
            }
        }
        assertTrue(dances >= 8);
        assertTrue(Integer.bitCount(styles) >= 3);
    }

    @Test
    void sitImagesAreRefusedOnTheScreenFloorAndFaceTheVoidAtAnEdge() {
        PetResources.FullPose sit = new PetResources.FullPose("sit", null, 100, 200, 70, 130, 20, 100, 0.6f, 35, 45, -25);
        PetResources.Rig rig = rig(sit);
        PetBrain brain = new PetBrain(8);
        PetMotion motion = motion();
        PetPose puppet = new PetPose();
        PetFullPose images = new PetFullPose();
        brain.state = PetState.SIT;
        for (int i = 0; i < 50; i++) images.update(0.05f, rig, "sit", brain, motion, puppet, 0);
        assertEquals(0, images.blend);
        motion.elevated = motion.atEdge = true;
        motion.edgeDirection = 1;
        for (int i = 0; i < 60; i++) {
            images.update(0.05f, rig, null, brain, motion, puppet, 0);
            puppet.update(brain, rig, motion, 0.05f);
        }
        assertTrue(images.ready("sit"));
        assertEquals(-1, motion.direction);
    }

    @Test
    void smokeStaysAPuppetAndGreetingsAreQueuedAtJoinAndReturn() {
        PetResources.FullPose swagger = new PetResources.FullPose("swagger", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, 0);
        PetResources.Rig rig = rig(swagger);
        PetBrain brain = new PetBrain(7);
        PetMotion motion = motion();
        PetPose puppet = new PetPose();
        PetFullPose images = new PetFullPose();
        brain.state = PetState.SMOKE;
        for (int i = 0; i < 200; i++) images.update(0.05f, rig, null, brain, motion, puppet, 1);
        assertNull(images.current);
        brain.reset();
        brain.update(0.05f, false, 1, 0, false);
        assertEquals(PetState.WAVE, brain.state);
        for (int i = 0; i < 3000; i++) brain.update(0.05f, false, 1, 0, false);
        brain.state = PetState.WALK;
        brain.age = 0;
        brain.duration = 100;
        brain.greet();
        for (int i = 0; i < 1000 && brain.state != PetState.WAVE; i++) brain.update(0.05f, false, 1, 0, false);
        assertEquals(PetState.WAVE, brain.state);
    }

    @Test
    void imageTransformsAndDanceShufflingAllocateNothingAfterWarmup() {
        var allocations = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        PetResources.FullPose a = new PetResources.FullPose("dance_1", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, 0);
        PetResources.FullPose b = new PetResources.FullPose("dance_3", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, 0);
        PetResources.FullPose c = new PetResources.FullPose("dance_17", null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, 0);
        PetDanceSequence sequence = new PetDanceSequence(32);
        sequence.start(new PetResources.FullPose[]{a, b, c}, new int[3], new int[3], a);
        PetImageTransform transform = new PetImageTransform();
        animateImages(sequence, transform, 100000);
        long thread = Thread.currentThread().threadId();
        long minimum = Long.MAX_VALUE;
        for (int sample = 0; sample < 5; sample++) {
            long before = allocations.getThreadAllocatedBytes(thread);
            animateImages(sequence, transform, 10000);
            minimum = Math.min(minimum, allocations.getThreadAllocatedBytes(thread) - before);
        }
        assertEquals(0, minimum);
    }

    private static void animateImages(PetDanceSequence sequence, PetImageTransform transform, int ticks) {
        for (int i = 0; i < ticks; i++) {
            sequence.update(0.05f);
            transform.update(sequence.current, 360, 100, i * 0.05f, (i & 1) == 0 ? 1 : -1, 1, 300, 600);
        }
    }

    @Test
    void rendererUsesTheDisplayedImageEyesForHeadAnchors() {
        PetResources.FullPose a = new PetResources.FullPose("sleep", null, 300, 90, 150, 89, 20, 70, 0.5f, 55, 20, 0);
        PetResources.FullPose b = new PetResources.FullPose("dance_3", null, 100, 200, 60, 199, 20, 100, 0.7f, 35, 40, 0);
        PetResources.Rig rig = rig(a, b);
        PetMotion motion = motion();
        PetPose puppet = new PetPose();
        PetFullPose images = new PetFullPose();
        PetRenderer renderer = new PetRenderer();
        images.current = a;
        images.alternate = b;
        images.blend = images.previousBlend = 1;
        images.mix = images.previousMix = 0.25f;
        for (int direction = -1; direction <= 1; direction += 2) {
            motion.direction = direction;
            renderer.prepare(rig, motion, puppet, 90, 1);
            renderer.eyes(rig, motion, images, 90, 1);
            float expectedX = motion.x + direction * ((a.eyesX() - a.feetX()) * a.scale() * 0.75f
                + (b.eyesX() - b.feetX()) * b.scale() * 0.25f) * 90 / rig.height();
            float expectedY = motion.y + ((a.eyesY() - a.feetY()) * a.scale() * 0.75f
                + (b.eyesY() - b.feetY()) * b.scale() * 0.25f) * 90 / rig.height();
            assertEquals(expectedX, renderer.anchorX, 0.001f);
            assertEquals(expectedY, renderer.anchorY, 0.001f);
        }
    }

    @Test
    void returningGreetingWakesAfterTheSleepMinimumAndNotBefore() {
        PetBrain brain = new PetBrain(43);
        brain.state = PetState.SETTLE;
        brain.duration = 1;
        for (int i = 0; i < 200 && brain.state != PetState.SLEEP; i++) brain.update(0.05f, true, 1, 0, false);
        assertEquals(PetState.SLEEP, brain.state);
        brain.duration = 120;
        brain.greet();
        int sleeping = 0;
        while (brain.state == PetState.SLEEP && sleeping < 3000) {
            sleeping++;
            brain.update(0.05f, false, 1, 0, false);
        }
        assertTrue(sleeping >= 600 && sleeping <= 602);
        for (int i = 0; i < 300 && brain.state != PetState.WAVE; i++) brain.update(0.05f, false, 1, 0, false);
        assertEquals(PetState.WAVE, brain.state);
    }

    @Test
    void danceSequencesAreNotLimitedToAFixedFrameCount() {
        PetResources.FullPose[] frames = new PetResources.FullPose[257];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new PetResources.FullPose("dance_" + (i + 1), null, 100, 200, 50, 199, 20, 100, 0.5f, 50, 50, 0);
        }
        PetResources.Rig rig = rig(frames);
        assertEquals(257, rig.dances().length);
        PetDanceSequence sequence = new PetDanceSequence(21);
        sequence.start(rig.dances(), rig.danceOrder(), rig.previousDanceOrder(), frames[0]);
        boolean[] seen = new boolean[frames.length];
        int count = 0;
        for (int tick = 0; tick < 6000; tick++) {
            sequence.update(0.05f);
            int index = PetResources.danceNumber(sequence.current.name()) - 1;
            if (!seen[index]) { seen[index] = true; count++; }
        }
        assertEquals(257, count);
    }

}
