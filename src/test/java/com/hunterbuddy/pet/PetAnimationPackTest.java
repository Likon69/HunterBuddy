package com.hunterbuddy.pet;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PetAnimationPackTest {
    private static final String GOOD = """
        {"animations":[
          {"name":"wave_hand","duration":2,"weight":3,"props":{"smoke":true},"keyframes":[
            {"t":0,"easing":"linear","joints":{"left_arm":0}},
            {"t":0.5,"easing":"smooth","joints":{"left_arm":90,"left_leg_lift":10,"crouch":0.2}},
            {"t":1,"joints":{}}]},
          {"name":"broken","duration":2,"keyframes":[{"t":0,"joints":{"left_elbow":3}}]},
          {"name":"too_long","duration":900,"keyframes":[{"t":0,"joints":{}}]},
          {"name":"wave_hand","duration":1,"keyframes":[{"t":0,"joints":{}}]},
          {"duration":1,"keyframes":[{"t":0,"joints":{}}]},
          {"name":"empty","duration":1,"keyframes":[]}
        ]}
        """;

    private static PetAnimation[] parse(String text, List<String> log) {
        return PetAnimationPack.parse(text, log::add);
    }

    @Test
    void malformedAnimationsAreSkippedWithOneLineEach() {
        List<String> log = new ArrayList<>();
        PetAnimation[] result = parse(GOOD, log);
        assertEquals(1, result.length);
        assertEquals("wave_hand", result[0].name());
        assertEquals(3, result[0].weight());
        assertTrue(result[0].smoke());
        assertEquals(5, log.size());
        assertTrue(log.get(0).contains("broken") && log.get(0).contains("left_elbow"));
    }

    @Test
    void unreadableFileGivesNoAnimations() {
        List<String> log = new ArrayList<>();
        assertEquals(0, parse("not json", log).length);
        assertEquals(0, parse("{}", log).length);
        assertEquals(2, log.size());
    }

    @Test
    void degreesBecomeRadiansAndEasingsApply() {
        PetAnimation animation = parse(GOOD, new ArrayList<>())[0];
        float[] out = new float[PetAnimation.JOINTS.length];
        animation.sample(1, out);
        assertEquals(Math.PI / 2, out[2], 1e-5);
        assertEquals(10, out[PetAnimation.LEFT_LIFT], 1e-5);
        assertEquals(0.2f, out[PetAnimation.CROUCH], 1e-5);
    }

    @Test
    void smoothSegmentIsSlowerAtTheStartThanLinear() {
        PetAnimation animation = parse(GOOD, new ArrayList<>())[0];
        float[] out = new float[PetAnimation.JOINTS.length];
        animation.sample(0.5f, out);
        assertEquals((float) Math.PI / 2 * 0.5f, out[2], 1e-5);
        animation.sample(1.25f, out);
        assertEquals((float) Math.PI / 2 * (1 - 0.15625f), out[2], 1e-4);
    }

    @Test
    void oneShotHoldsTheEndsAndLoopsWrapToTheFirstKey() {
        String text = "{\"animations\":[{\"name\":\"a\",\"duration\":1,\"loops\":3,\"keyframes\":["
            + "{\"t\":0.25,\"easing\":\"linear\",\"joints\":{\"head\":10}},{\"t\":0.75,\"easing\":\"linear\",\"joints\":{\"head\":30}}]},"
            + "{\"name\":\"b\",\"duration\":1,\"keyframes\":["
            + "{\"t\":0.25,\"easing\":\"linear\",\"joints\":{\"head\":10}},{\"t\":0.75,\"easing\":\"linear\",\"joints\":{\"head\":30}}]}]}";
        PetAnimation[] result = parse(text, new ArrayList<>());
        float[] out = new float[PetAnimation.JOINTS.length];
        assertEquals(3, result[0].length(), 1e-6);
        result[0].sample(2.0f, out);
        assertEquals(Math.toRadians(20), out[0], 1e-5);
        result[0].sample(1.0f, out);
        assertEquals(Math.toRadians(20), out[0], 1e-5);
        result[0].sample(1.25f, out);
        assertEquals(Math.toRadians(10), out[0], 1e-5);
        result[1].sample(0, out);
        assertEquals(Math.toRadians(10), out[0], 1e-5);
        result[1].sample(5, out);
        assertEquals(Math.toRadians(30), out[0], 1e-5);
    }

    @Test
    void brainPicksCustomByWeightWithoutBackToBackRepeatsAndNeverInDanger() {
        PetAnimation[] animations = parse(GOOD.replace("\"weight\":3", "\"weight\":50"), new ArrayList<>());
        List<String> log = new ArrayList<>();
        PetBrain brain = new PetBrain(9);
        brain.animations = animations;
        brain.announce = log::add;
        int customRuns = 0;
        PetState last = null;
        for (int tick = 0; tick < 72000; tick++) {
            brain.externalDanger = tick % 20000 >= 15000;
            PetState before = brain.state;
            brain.update(0.05f, false, 1, 0, true);
            if (brain.state == PetState.CUSTOM && before != PetState.CUSTOM) {
                customRuns++;
                assertFalse(brain.danger(), "Custom animation began during danger");
                assertSame(animations[0], brain.animation);
                assertEquals(animations[0].length(), brain.duration, 1e-5);
            }
            if (before != brain.state) {
                assertFalse(before == last && brain.state == last);
                last = brain.state;
            }
        }
        assertTrue(customRuns >= 5, "Custom animations never ran: " + customRuns);
        log.removeIf(line -> line.startsWith("[pet] state "));
        assertEquals(customRuns, log.size());
        assertEquals("[pet] animation wave_hand", log.get(0));
    }

    @Test
    void zeroWeightAnimationsNeverRun() {
        PetAnimation[] animations = parse(GOOD.replace("\"weight\":3", "\"weight\":0"), new ArrayList<>());
        PetBrain brain = new PetBrain(4);
        brain.animations = animations;
        for (int tick = 0; tick < 72000; tick++) {
            brain.update(0.05f, false, 1, 0, true);
            assertNotEquals(PetState.CUSTOM, brain.state);
        }
    }

    @Test
    void poseFollowsTheKeyframesAndLiftsTheFoot() {
        PetAnimation animation = parse(GOOD, new ArrayList<>())[0];
        PetResources.Rig rig = PetAnimationTest.rig();
        PetBrain brain = new PetBrain(1);
        PetPose pose = new PetPose();
        PetMotion motion = new PetMotion(5);
        motion.width = 1280;
        motion.height = 720;
        motion.x = motion.previousX = 300;
        motion.y = motion.previousY = 650;
        motion.travel = PetMotion.Travel.GROUNDED;
        brain.state = PetState.CUSTOM;
        brain.animation = animation;
        brain.duration = animation.length();
        for (int i = 0; i < 40; i++) {
            brain.age = 1;
            pose.update(brain, rig, motion, 0.05f);
        }
        assertEquals(Math.PI / 2, pose.angles[2], 0.12);
        assertEquals(10, pose.legLift[0], 1.5);
        assertEquals(0.2f, pose.crouch, 0.03);
        PetSkeleton grounded = new PetSkeleton();
        pose.reset();
        grounded.update(rig, motion, pose, 100, 1);
        pose.legLift[0] = pose.previousLegLift[0] = 10;
        PetSkeleton lifted = new PetSkeleton();
        lifted.update(rig, motion, pose, 100, 1);
        assertEquals(grounded.matrices[4].m21() - 10 * 100f / rig.height(), lifted.matrices[4].m21(), 1e-3);
        assertEquals(grounded.matrices[5].m21(), lifted.matrices[5].m21(), 1e-3);
    }
}
