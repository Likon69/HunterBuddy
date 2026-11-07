package com.hunterbuddy.pet;

import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PetAnimationTest {
    static PetResources.Part part(int index, float x, float y, float px, float py, int w, int h) {
        return new PetResources.Part(index, null, x, y, px, py, w, h, new float[]{0, 0, w, 0, w, h, 0, h}, index);
    }

    static PetResources.Rig rig(PetResources.FullPose... images) {
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
        PetMotion motion = new PetMotion(77);
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
        int armsSettling = 0;
        int sleepTicks = 0;
        try {
            for (int tick = 0; tick < 36000; tick++) {
                brain.elevated = tick % 3600 >= 2400;
                brain.seatReachable = brain.elevated;
                brain.highEdge = brain.elevated;
                brain.surfaceKind = brain.elevated ? 3 : 0;
                motion.highEdge = brain.highEdge;
                brain.atEdge = motion.atEdge = brain.highEdge;
                brain.roomToLie = !brain.elevated;
                brain.externalDanger = tick % 7000 >= 6800;
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
                boolean raised = brain.state == PetState.SMOKE || brain.state == PetState.PANIC || brain.state == PetState.CELEBRATE;
                armsSettling = raised ? 40 : Math.max(0, armsSettling - 1);
                if (armsSettling == 0) assertTrue(Math.abs(pose.angles[2]) <= 0.601f);
                if (brain.state != PetState.DRINK && Math.abs(pose.angles[3]) < 0.2f) assertTrue(Math.abs(pose.angles[3]) <= 0.201f);
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
                if (brain.state != PetState.DRINK && !brain.resting() && Math.abs(pose.angles[3]) < 0.2f) {
                    skeleton.matrices[1].invert(inverse);
                    skeleton.anchor(rig, 0);
                    inverse.transformPosition(skeleton.anchorX, skeleton.anchorY, local);
                    assertTrue(local.x < 86, "Left hand crossed the torso");
                    skeleton.anchor(rig, 1);
                    inverse.transformPosition(skeleton.anchorX, skeleton.anchorY, local);
                    assertTrue(local.x > 166, "Right hand crossed the torso");
                }
                if (!brain.resting() && pose.fold < 0.01f) {
                    skeleton.matrices[1].invert(inverse);
                    for (PetResources.Part torso : rig.parts()) {
                        if (torso.index() != 1) continue;
                        for (int anchor = 0; anchor < 2; anchor++) {
                            skeleton.anchor(rig, anchor);
                            inverse.transformPosition(skeleton.anchorX, skeleton.anchorY, local);
                            boolean inside = local.x > torso.x() && local.x < torso.x() + torso.width() && local.y > rig.parts()[3].pivotY();
                            assertFalse(inside, "Hand anchor " + anchor + " is inside the torso span in " + brain.state);
                        }
                    }
                }
            }
        } finally {
            PetEvents.listen(false);
        }
        System.out.printf("Simulated ticks: 36000; completed sleeps: %d; seated activities: %d%n", sleeps, sits);
        assertTrue(sleeps >= 2, "No sustained sleeps in thirty minutes");
        assertTrue(sits >= 1, "Elevated surfaces did not encourage sitting");
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
        assertTrue(pose.angles[0] < -0.25f && pose.angles[0] >= -0.52f);
        for (int direction = -1; direction <= 1; direction += 2) {
            motion.direction = direction;
            skeleton.update(rig, motion, pose, 100, 1);
            skeleton.anchor(rig, 1);
            float x = skeleton.anchorX;
            float y = skeleton.anchorY;
            skeleton.anchor(rig, 2);
            assertEquals(x, skeleton.anchorX, 2f);
            assertEquals(y, skeleton.anchorY, 2f);
        }
    }

    @Test
    void optionalImagesWaitForCalmTurnAndCrossFadeWithoutJumps() {
        PetResources.FullPose image = new PetResources.FullPose("moon", null, 100, 200, 50, 199, 0.5f, 50, 50, -20);
        PetResources.FullPose first = new PetResources.FullPose("dance_1", null, 100, 200, 50, 199, 0.5f, 50, 50, -20);
        PetResources.FullPose second = new PetResources.FullPose("dance_2", null, 100, 200, 50, 199, 0.5f, 50, 50, -20);
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
            poses.update(0.05f, rig, "moon", brain, motion, puppet);
            puppet.update(brain, rig, motion, 0.05f);
            assertTrue(Math.abs(poses.blend - poses.previousBlend) <= 0.2001f);
            if (poses.blend > 0 && !firstVisible) {
                assertTrue(tick >= 11);
                assertEquals(-direction, motion.direction);
                assertTrue(puppet.calm());
                firstVisible = true;
            }
        }
        assertTrue(poses.ready("moon"));
        boolean changed = false;
        boolean mixed = false;
        for (int tick = 0; tick < 300; tick++) {
            poses.update(0.05f, rig, null, brain, motion, puppet);
            puppet.update(brain, rig, motion, 0.05f);
            assertTrue(Math.abs(poses.blend - poses.previousBlend) <= 0.2001f);
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
        PetMotion motion = new PetMotion(77);
        PetBrain brain = new PetBrain(14);
        brain.state = PetState.SIT;
        motion.update(brain, 0.05f, surfaces, 100, 67, false);
        motion.x = 120;
        motion.y = 600;
        motion.travel = PetMotion.Travel.GROUNDED;
        for (int i = 0; i < 200; i++) motion.update(brain, 0.05f, surfaces, 100, 67, false);
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
        PetResources.FullPose image = new PetResources.FullPose("sleep", null, 100, 200, 50, 199, 0.5f, 50, 50, -20);
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
            brain.update(0.05f, i < 700, 1, 0, false);
            poses.update(0.05f, rig, null, brain, motion, puppet);
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
        PetResources.FullPose image = new PetResources.FullPose("moon", null, 100, 200, 50, 199, 0.5f, 50, 50, -20);
        PetResources.Rig rig = rig(image);
        PetBrain brain = new PetBrain(10);
        PetMotion motion = motion();
        PetPose puppet = new PetPose();
        PetFullPose poses = new PetFullPose();
        motion.travel = PetMotion.Travel.JUMP;
        for (int i = 0; i < 100; i++) poses.update(0.05f, rig, "moon", brain, motion, puppet);
        assertEquals(0, poses.blend);
        assertFalse(poses.blocksMotion());
        motion.travel = PetMotion.Travel.GROUNDED;
        brain.state = PetState.SLEEP;
        brain.age = 10;
        puppet.fold = 1;
        for (int i = 0; i < 100; i++) poses.update(0.05f, rig, "moon", brain, motion, puppet);
        assertEquals(0, poses.blend);
        assertFalse(brain.preparingPose);
    }

    @Test
    void animationAndImageTransitionsAllocateNothingAfterWarmup() {
        var allocations = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        PetResources.Rig rig = rig(new PetResources.FullPose("swagger", null, 100, 200, 50, 199, 0.5f, 50, 50, -20));
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
            brain.danceImageCount = rig.dances().length;
            brain.update(0.05f, true, 1, 0, true);
            images.update(0.05f, rig, null, brain, motion, pose);
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
        PetResources.FullPose sit = new PetResources.FullPose("sit", null, 100, 200, 70, 130, 0.6f, 35, 45, -25);
        PetResources.FullPose sleep = new PetResources.FullPose("sleep", null, 300, 90, 150, 89, 0.5f, 55, 20, 0);
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
        PetResources.FullPose first = new PetResources.FullPose("dance_1", null, 100, 200, 50, 199, 0.5f, 50, 50, 0);
        PetResources.FullPose third = new PetResources.FullPose("dance_3", null, 100, 200, 50, 199, 0.5f, 50, 50, 0);
        PetResources.FullPose later = new PetResources.FullPose("dance_17", null, 100, 200, 50, 199, 0.5f, 50, 50, 0);
        PetResources.FullPose invalid = new PetResources.FullPose("dance_x", null, 100, 200, 50, 199, 0.5f, 50, 50, 0);
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
        boolean lastImage = true;
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
        PetResources.FullPose sit = new PetResources.FullPose("sit", null, 100, 200, 70, 130, 0.6f, 35, 45, -25);
        PetResources.Rig rig = rig(sit);
        PetBrain brain = new PetBrain(8);
        PetMotion motion = motion();
        PetPose puppet = new PetPose();
        PetFullPose images = new PetFullPose();
        brain.state = PetState.SIT;
        brain.imageSit = true;
        for (int i = 0; i < 50; i++) images.update(0.05f, rig, "sit", brain, motion, puppet);
        assertEquals(0, images.blend);
        motion.elevated = motion.atEdge = true;
        motion.edgeDirection = 1;
        for (int i = 0; i < 60; i++) {
            images.update(0.05f, rig, null, brain, motion, puppet);
            puppet.update(brain, rig, motion, 0.05f);
        }
        assertTrue(images.ready("sit"));
        assertEquals(-1, motion.direction);
    }

    @Test
    void directionNeverOscillatesAndSoundsRespectTheirIntervalsOverEveryActivity() {
        PetSurfaces surfaces = PetSurfaces.INSTANCE;
        surfaces.enabled = true;
        surfaces.bottom = true;
        float[] zero = new float[PetAnimation.JOINTS.length];
        float[] shifted = zero.clone();
        shifted[PetAnimation.SHIFT] = -20;
        PetAnimation[] animations = {
            new PetAnimation("side_step", 2.8f, 1, 0.4f, false, new PetAnimation.Key[]{
                new PetAnimation.Key(0, true, zero), new PetAnimation.Key(0.5f, true, shifted), new PetAnimation.Key(1, true, zero)}),
            new PetAnimation("rock", 1.6f, 3, 0.3f, false, new PetAnimation.Key[]{
                new PetAnimation.Key(0, true, zero), new PetAnimation.Key(0.5f, true, shifted)})
        };
        PetResources.Rig rig = rig();
        long[] lastStart = new long[PetSfxAction.ALL.length];
        long[] clock = new long[1];
        PetSfx sfx = new PetSfx(new PetSfx.Backend() {
            @Override
            public boolean active(int slot) {
                return false;
            }

            @Override
            public boolean start(PetSfxAction action, int slot, float volume) {
                int index = action.ordinal();
                if (lastStart[index] != 0) assertTrue(clock[0] - lastStart[index] >= action.interval, action.key);
                lastStart[index] = clock[0];
                return true;
            }

            @Override
            public void stop(int slot) {
            }
        });
        PetSfxCues cues = new PetSfxCues(sfx);
        PetFullPose images = new PetFullPose();
        for (long seed = 1; seed <= 3; seed++) {
            PetBrain brain = new PetBrain(seed);
            brain.reset();
            brain.worldStable = true;
            brain.animations = animations;
            PetMotion motion = new PetMotion(seed);
            motion.reset();
            PetPose puppet = new PetPose();
            cues.reset();
            sfx.reset();
            java.util.Arrays.fill(lastStart, 0);
            int flips = 0;
            int lastFlipTick = -100;
            int previousDirection = motion.direction;
            for (int tick = 0; tick < 20 * 60 * 20; tick++) {
                clock[0] = tick * 50L + 1;
                sfx.update(clock[0], true, false, 1);
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
                PetMotion.Travel before = motion.travel;
                motion.update(brain, 0.05f, surfaces, 80, 53, brain.resting());
                puppet.update(brain, rig, motion, 0.05f);
                cues.update(brain, motion, puppet, images);
                if (motion.direction != previousDirection) {
                    boolean deliberate = brain.state == PetState.TURN || before != PetMotion.Travel.JUMP && motion.travel == PetMotion.Travel.JUMP;
                    if (!deliberate) {
                        assertTrue(tick - lastFlipTick >= 11, "seed " + seed + " tick " + tick + " state " + brain.state);
                        lastFlipTick = tick;
                        flips++;
                    }
                }
                previousDirection = motion.direction;
            }
            assertTrue(flips < 20 * 60 * 20 / 12, "flips " + flips);
        }
    }

    @Test
    void texturePaddingDoesNotChangeTheDrinkingOrAnyPoseGeometry() {
        PetResources.Rig plain = rig();
        PetResources.Part[] padded = new PetResources.Part[plain.parts().length];
        for (int i = 0; i < padded.length; i++) {
            PetResources.Part p = plain.parts()[i];
            float[] outline = p.outline().clone();
            for (int k = 0; k < outline.length; k++) outline[k] += 4;
            padded[i] = new PetResources.Part(p.index(), null, p.x() - 4, p.y() - 4, p.pivotX(), p.pivotY(), p.width() + 8, p.height() + 8, outline, p.z());
        }
        PetResources.Rig wide = new PetResources.Rig(plain.width(), plain.height(), padded, plain.anchors(), null, 44, 107, 22, 4,
            plain.poses(), plain.dances(), plain.danceOrder(), plain.previousDanceOrder());
        PetBrain brain = new PetBrain(3);
        brain.reset();
        brain.state = PetState.DRINK;
        brain.duration = 6;
        PetPose first = new PetPose();
        PetPose second = new PetPose();
        PetMotion motion = motion();
        PetSkeleton a = new PetSkeleton();
        PetSkeleton b = new PetSkeleton();
        for (int tick = 0; tick < 120; tick++) {
            brain.age = tick * 0.05f;
            first.update(brain, plain, motion, 0.05f);
            second.update(brain, wide, motion, 0.05f);
            a.update(plain, motion, first, 100, 1);
            b.update(wide, motion, second, 100, 1);
            for (int i = 0; i < 6; i++) {
                assertEquals(first.angles[i], second.angles[i], 1e-6);
                for (float[] m : new float[][]{{a.matrices[i].m20(), b.matrices[i].m20()}, {a.matrices[i].m21(), b.matrices[i].m21()}})
                    assertEquals(m[0], m[1], 1e-4);
            }
        }
        assertTrue(first.drink.available);
    }

    @Test
    void seatedDrinksHappenDuringLongSitsNeverInDangerAndNeverOutsideTheSeat() {
        int drinks = 0;
        for (long seed = 0; seed < 80; seed++) {
            for (boolean danger : new boolean[]{false, true}) {
                PetBrain brain = new PetBrain(seed);
                brain.reset();
                brain.grounded = true;
                brain.elevated = true;
                brain.atEdge = true;
                brain.externalDanger = danger;
                brain.state = PetState.SIT;
                brain.duration = 60;
                brain.age = 0;
                boolean drank = false;
                for (int tick = 0; tick < 20 * 62; tick++) {
                    brain.update(0.05f, false, 1, 0, true);
                    if (!brain.sitDrink) continue;
                    assertEquals(PetState.SIT, brain.state);
                    assertFalse(brain.resting());
                    assertFalse(danger);
                    drank = true;
                }
                if (drank) drinks++;
            }
        }
        assertEquals(80, drinks);
    }

    @Test
    void everySitPlansASeatedDrinkRetriesWhenBlockedAndLogsWhy() {
        for (long seed = 0; seed < 40; seed++) {
            java.util.List<String> log = new java.util.ArrayList<>();
            PetBrain brain = new PetBrain(seed);
            brain.reset();
            brain.announce = log::add;
            brain.grounded = true;
            brain.elevated = true;
            brain.atEdge = true;
            brain.state = PetState.SIT;
            brain.duration = 60;
            brain.age = 0;
            brain.spoke();
            boolean drank = false;
            for (int tick = 0; tick < 20 * 62; tick++) {
                brain.update(0.05f, false, 1, 0, true);
                drank |= brain.sitDrink;
            }
            assertTrue(drank, "seed " + seed + " " + log);
            assertTrue(log.get(0).startsWith("[pet] seated drink planned "), log.toString());
            assertTrue(log.contains("[pet] seated drink"), log.toString());
            assertTrue(log.stream().filter(line -> line.startsWith("[pet] seated drink blocked")).count() <= 1);
        }
        java.util.List<String> log = new java.util.ArrayList<>();
        PetBrain brain = new PetBrain(3);
        brain.reset();
        brain.announce = log::add;
        brain.grounded = true;
        brain.elevated = true;
        brain.atEdge = true;
        brain.externalDanger = true;
        brain.state = PetState.SIT;
        brain.duration = 60;
        for (int tick = 0; tick < 20 * 62; tick++) {
            brain.update(0.05f, false, 1, 0, true);
            assertFalse(brain.sitDrink);
        }
        assertTrue(log.contains("[pet] seated drink blocked: danger"), log.toString());
    }

    private static final class SitRig {
        final PetResources.Rig rig = rig(new PetResources.FullPose("sit", null, 100, 200, 50, 150, 0.5f, 50, 50, 20));
        final PetBrain brain = new PetBrain(4);
        final PetMotion motion = motion();
        final PetPose puppet = new PetPose();
        final PetFullPose images = new PetFullPose();

        SitRig(boolean imageSit) {
            brain.reset();
            motion.elevated = true;
            motion.atEdge = true;
            motion.edgeDirection = 1;
            brain.state = PetState.SIT;
            brain.imageSit = imageSit;
            brain.duration = 200;
            brain.grounded = true;
            brain.elevated = true;
            brain.atEdge = true;
        }

        void tick() {
            brain.update(0.05f, false, 1, 0, true);
            images.update(0.05f, rig, null, brain, motion, puppet);
            puppet.update(brain, rig, motion, 0.05f);
        }
    }

    @Test
    void anImageSitHoldsTheImageForTheWholeSitWithoutAnySeatedDrink() {
        for (long seed = 0; seed < 20; seed++) {
            SitRig sit = new SitRig(true);
            java.util.List<String> log = new java.util.ArrayList<>();
            sit.brain.announce = log::add;
            sit.brain.worldStable = true;
            for (int tick = 0; tick < 100 && !sit.images.ready("sit"); tick++) sit.tick();
            assertTrue(sit.images.ready("sit"));
            for (int tick = 0; tick < 20 * 180; tick++) {
                sit.tick();
                assertTrue(sit.images.ready("sit"), "tick " + tick);
                assertFalse(sit.brain.sitDrink);
                assertEquals(PetState.SIT, sit.brain.state);
            }
            assertTrue(log.stream().noneMatch(line -> line.startsWith("[pet] seated drink")), log.toString());
        }
    }

    @Test
    void aOneTickEdgeFlickerKeepsTheSitImageAndARealSeatLossExitsItOnceForTheWholeSit() {
        SitRig sit = new SitRig(true);
        for (int tick = 0; tick < 100 && !sit.images.ready("sit"); tick++) sit.tick();
        assertTrue(sit.images.ready("sit"));
        for (int tick = 0; tick < 40; tick++) sit.tick();
        sit.brain.atEdge = sit.motion.atEdge = false;
        sit.tick();
        sit.brain.atEdge = sit.motion.atEdge = true;
        for (int tick = 0; tick < 40; tick++) {
            sit.tick();
            assertTrue(sit.images.ready("sit"));
            assertEquals(1, sit.images.blend);
        }
        sit.brain.atEdge = sit.motion.atEdge = false;
        sit.brain.elevated = sit.motion.elevated = false;
        sit.brain.seeking = true;
        boolean exited = false;
        for (int tick = 0; tick < 20 * 4; tick++) {
            sit.tick();
            exited |= !sit.images.ready("sit");
        }
        assertTrue(exited);
        assertEquals(PetState.SIT, sit.brain.state);
        assertFalse(sit.brain.imageSit);
        sit.brain.atEdge = sit.motion.atEdge = true;
        sit.brain.elevated = sit.motion.elevated = true;
        sit.brain.seeking = false;
        for (int tick = 0; tick < 20 * 40; tick++) {
            sit.tick();
            assertFalse(sit.images.ready("sit"));
            assertEquals(PetState.SIT, sit.brain.state);
        }
    }

    @Test
    void aPuppetSitDrinksAndNeverShowsTheSitImage() {
        for (long seed = 0; seed < 20; seed++) {
            SitRig sit = new SitRig(false);
            boolean drank = false;
            for (int tick = 0; tick < 20 * 180; tick++) {
                sit.tick();
                drank |= sit.brain.sitDrink;
                assertFalse(sit.images.ready("sit"));
                assertEquals(0, sit.images.blend);
                assertEquals(PetState.SIT, sit.brain.state);
            }
            assertTrue(drank);
        }
    }

    @Test
    void aPuppetSitIgnoresALineThatAsksForTheSitImage() {
        SitRig sit = new SitRig(false);
        for (int tick = 0; tick < 100; tick++) {
            sit.images.update(0.05f, sit.rig, "sit", sit.brain, sit.motion, sit.puppet);
            sit.puppet.update(sit.brain, sit.rig, sit.motion, 0.05f);
            assertFalse(sit.images.ready("sit"));
        }
    }

    @Test
    void sitsAlternateBetweenTheImageAndThePuppetAndADeniedImageTurnsIntoAPuppetSit() {
        PetBrain brain = new PetBrain(9);
        brain.reset();
        brain.elevated = brain.atEdge = brain.grounded = brain.seatReachable = true;
        boolean[] seen = new boolean[4];
        for (int i = 0; i < seen.length; i++) {
            brain.greeted();
            brain.state = PetState.WALK;
            brain.age = brain.duration;
            for (int tick = 0; tick < 20 * 600 && brain.state != PetState.SIT; tick++) {
                brain.update(0.05f, false, 0, 0, true);
                if (brain.state != PetState.SIT) brain.age = Math.max(brain.age, brain.duration);
            }
            assertEquals(PetState.SIT, brain.state);
            seen[i] = brain.imageSit;
            if (i == 2) brain.denyImage();
            if (i == 2) seen[i] = brain.imageSit;
            brain.age = brain.duration;
            brain.externalDanger = false;
        }
        assertTrue(seen[0]);
        assertFalse(seen[1]);
        assertFalse(seen[2]);
        assertTrue(seen[3]);
    }

    @Test
    void aSitSurvivesAnInstantSeatLossAndEndsOnlyWhenTheSeatStaysLost() {
        PetBrain brain = new PetBrain(6);
        brain.reset();
        java.util.List<String> log = new java.util.ArrayList<>();
        brain.announce = log::add;
        brain.state = PetState.SIT;
        brain.duration = 60;
        brain.grounded = true;
        brain.atEdge = false;
        brain.elevated = false;
        brain.seeking = false;
        for (int tick = 0; tick < 20; tick++) brain.update(0.05f, false, 1, 0, true);
        assertEquals(PetState.SIT, brain.state);
        brain.seeking = true;
        for (int tick = 0; tick < 20 * 10; tick++) brain.update(0.05f, false, 1, 0, true);
        assertEquals(PetState.SIT, brain.state);
        brain.seeking = false;
        for (int tick = 0; tick < 20 * 3; tick++) brain.update(0.05f, false, 1, 0, true);
        assertEquals(PetState.REST, brain.state);
        assertTrue(log.contains("[pet] state SIT -> REST"), log.toString());
    }

    @Test
    void anActivePlayerNeverMakesThePetSettleOrSleepAndInputWakesASleepingPet() {
        for (long seed = 0; seed < 10; seed++) {
            PetBrain brain = new PetBrain(seed);
            brain.reset();
            brain.grounded = brain.roomToLie = brain.worldStable = true;
            brain.drunkenness = 1;
            for (int tick = 0; tick < 20 * 3600; tick++) {
                brain.update(0.05f, false, 1, 0, true);
                assertFalse(brain.resting(), "seed " + seed + " tick " + tick + " " + brain.state);
            }
        }
        PetBrain brain = new PetBrain(3);
        brain.reset();
        brain.state = PetState.SLEEP;
        brain.duration = 100;
        brain.age = 5;
        brain.grounded = true;
        brain.update(0.05f, true, 1, 0, true);
        assertEquals(PetState.SLEEP, brain.state);
        brain.react(PetEvent.WAKE_UP, false, false);
        brain.update(0.05f, false, 1, 0, true);
        assertEquals(PetState.RISE, brain.state);
    }

    @Test
    void smokeStaysAPuppetAndGreetingsAreQueuedAtJoinAndReturn() {
        PetResources.FullPose swagger = new PetResources.FullPose("swagger", null, 100, 200, 50, 199, 0.5f, 50, 50, 0);
        PetResources.Rig rig = rig(swagger);
        PetBrain brain = new PetBrain(7);
        PetMotion motion = motion();
        PetPose puppet = new PetPose();
        PetFullPose images = new PetFullPose();
        brain.state = PetState.SMOKE;
        for (int i = 0; i < 200; i++) images.update(0.05f, rig, null, brain, motion, puppet);
        assertNull(images.current);
        brain.reset();
        brain.update(0.05f, false, 1, 0, false);
        brain.greeted();
        assertEquals(PetState.WAVE, brain.state);
        for (int i = 0; i < 3000; i++) brain.update(0.05f, false, 1, 0, false);
        assertNotEquals(PetState.WAVE, brain.state);
    }

    @Test
    void imageTransformsAndDanceShufflingAllocateNothingAfterWarmup() {
        var allocations = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(allocations.isThreadAllocatedMemorySupported());
        allocations.setThreadAllocatedMemoryEnabled(true);
        PetResources.FullPose a = new PetResources.FullPose("dance_1", null, 100, 200, 50, 199, 0.5f, 50, 50, 0);
        PetResources.FullPose b = new PetResources.FullPose("dance_3", null, 100, 200, 50, 199, 0.5f, 50, 50, 0);
        PetResources.FullPose c = new PetResources.FullPose("dance_17", null, 100, 200, 50, 199, 0.5f, 50, 50, 0);
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
        PetResources.FullPose a = new PetResources.FullPose("sleep", null, 300, 90, 150, 89, 0.5f, 55, 20, 0);
        PetResources.FullPose b = new PetResources.FullPose("dance_3", null, 100, 200, 60, 199, 0.7f, 35, 40, 0);
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
    void danceSequencesAreNotLimitedToAFixedFrameCount() {
        PetResources.FullPose[] frames = new PetResources.FullPose[257];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new PetResources.FullPose("dance_" + (i + 1), null, 100, 200, 50, 199, 0.5f, 50, 50, 0);
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
