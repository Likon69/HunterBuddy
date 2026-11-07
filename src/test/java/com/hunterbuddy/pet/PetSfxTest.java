package com.hunterbuddy.pet;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PetSfxTest {
    static ResourceManager resources(String json) {
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
            (proxy, method, args) -> {
                if (!method.getName().equals("getResourceOrThrow")) throw new UnsupportedOperationException();
                if (json == null) throw new FileNotFoundException();
                return new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            });
    }

    @Test
    void missingOrMalformedResourcesDisableSoundsAndReloadClearsTheOldBank() {
        assertSame(PetSfxBank.EMPTY, PetSfxBank.load(resources(null), id -> null, text -> { }));
        assertSame(PetSfxBank.EMPTY, PetSfxBank.load(resources("{"), id -> null, text -> { }));
        assertSame(PetSfxBank.EMPTY, PetSfxBank.load(resources("[]"), id -> null, text -> { }));
        assertSame(PetSfxBank.EMPTY, PetSfxBank.load(resources("{\"step\": 7}"), id -> null, text -> { }));
        PetResources.INSTANCE.reload(resources("{\"step\": []}"));
        assertNotSame(PetSfxBank.EMPTY, PetResources.INSTANCE.sfx);
        assertEquals(0, PetResources.INSTANCE.sfx.samples[PetSfxAction.STEP.ordinal()].length);
        int revision = PetResources.INSTANCE.revision;
        PetResources.INSTANCE.reload(resources("invalid"));
        assertSame(PetSfxBank.EMPTY, PetResources.INSTANCE.sfx);
        assertEquals(revision + 1, PetResources.INSTANCE.revision);
    }

    private static final class Backend implements PetSfx.Backend {
        final boolean[] active = new boolean[3];
        final int[] counts = new int[PetSfxAction.ALL.length];
        boolean available = true;
        int maximum;
        float volume;

        @Override
        public boolean active(int slot) {
            return active[slot];
        }

        @Override
        public boolean start(PetSfxAction action, int slot, float volume) {
            if (!available) return false;
            assertFalse(active[slot]);
            active[slot] = true;
            this.volume = volume;
            counts[action.ordinal()]++;
            int count = 0;
            for (boolean value : active) if (value) count++;
            maximum = Math.max(maximum, count);
            return true;
        }

        @Override
        public void stop(int slot) {
            active[slot] = false;
        }

        void finish() {
            for (int i = 0; i < active.length; i++) active[i] = false;
        }

        int count(PetSfxAction action) {
            return counts[action.ordinal()];
        }
    }

    @Test
    void limitsEachActionAndNeverOverlapsMoreThanThree() {
        Backend backend = new Backend();
        PetSfx sfx = new PetSfx(backend);
        for (long time = 0; time < 1800000; time += 10) {
            if (time % 400 == 0) backend.finish();
            sfx.update(time, true, false, 0.4f);
            for (PetSfxAction action : PetSfxAction.ALL) sfx.play(action);
        }
        assertEquals(3, backend.maximum);
        assertTrue(backend.count(PetSfxAction.STEP) > 100);
        assertEquals(0.4f, backend.volume);
        sfx.reset();
        backend.finish();
        sfx.update(0, true, false, 1);
        sfx.play(PetSfxAction.STEP);
        int first = backend.count(PetSfxAction.STEP);
        backend.finish();
        sfx.update(119, true, false, 1);
        sfx.play(PetSfxAction.STEP);
        assertEquals(first, backend.count(PetSfxAction.STEP));
        sfx.update(120, true, false, 1);
        sfx.play(PetSfxAction.STEP);
        assertEquals(first + 1, backend.count(PetSfxAction.STEP));
    }

    @Test
    void silenceStopsExistingSoundsAndOnlyAllowsScared() {
        Backend backend = new Backend();
        PetSfx sfx = new PetSfx(backend);
        sfx.update(0, true, false, 1);
        sfx.play(PetSfxAction.SNORE);
        sfx.update(100, true, true, 1);
        assertFalse(backend.active(0));
        for (PetSfxAction action : PetSfxAction.ALL) sfx.play(action);
        for (PetSfxAction action : PetSfxAction.ALL)
            assertEquals(action == PetSfxAction.SCARED || action == PetSfxAction.SNORE ? 1 : 0, backend.count(action));
        sfx.update(200, false, false, 1);
        assertFalse(backend.active(0));
        sfx.play(PetSfxAction.STEP);
        assertEquals(0, backend.count(PetSfxAction.STEP));
        sfx.update(300, true, false, 0);
        sfx.play(PetSfxAction.STEP);
        assertEquals(0, backend.count(PetSfxAction.STEP));
    }

    @Test
    void scaredCanReplaceASoundWhenAllVoicesAreBusy() {
        Backend backend = new Backend();
        PetSfx sfx = new PetSfx(backend);
        sfx.update(0, true, false, 1);
        sfx.play(PetSfxAction.STEP);
        sfx.play(PetSfxAction.JUMP);
        sfx.play(PetSfxAction.SNORE);
        sfx.play(PetSfxAction.SCARED);
        assertEquals(1, backend.count(PetSfxAction.SCARED));
        assertEquals(3, backend.maximum);
    }

    @Test
    void unavailableSamplesDoNotOccupyVoicesOrDelayOtherSounds() {
        Backend backend = new Backend();
        PetSfx sfx = new PetSfx(backend);
        sfx.update(0, true, false, 1);
        backend.available = false;
        for (PetSfxAction action : PetSfxAction.ALL) sfx.play(action);
        backend.available = true;
        sfx.play(PetSfxAction.STEP);
        assertEquals(1, backend.count(PetSfxAction.STEP));
        sfx.reset();
        assertFalse(backend.active(0));
    }

    @Test
    void cuesFollowContactsTransitionsAndVisiblePoseBeats() {
        Backend backend = new Backend();
        PetSfx sfx = new PetSfx(backend);
        PetSfxCues cues = new PetSfxCues(sfx);
        PetBrain brain = new PetBrain(1001);
        PetMotion motion = new PetMotion(77);
        PetPose puppet = new PetPose();
        PetFullPose full = new PetFullPose();
        brain.state = PetState.WALK;
        motion.travel = PetMotion.Travel.GROUNDED;
        motion.x = 10;
        puppet.previousAngles[4] = -0.1f;
        puppet.angles[4] = 0.1f;
        for (int frame = 0; frame < 11; frame++) {
            backend.finish();
            sfx.update(frame * 2000L, true, false, 1);
            switch (frame) {
                case 1 -> motion.travel = PetMotion.Travel.JUMP;
                case 2 -> motion.travel = PetMotion.Travel.FALL;
                case 3 -> motion.travel = PetMotion.Travel.GROUNDED;
                case 4 -> { brain.state = PetState.DRINK; brain.duration = 10; brain.age = 1.2f; }
                case 5 -> { brain.state = PetState.SIT; motion.atEdge = true; puppet.seated = 1; }
                case 6 -> { brain.state = PetState.LIE_DOWN; }
                case 7 -> brain.state = PetState.RISE;
                case 8 -> brain.state = PetState.PANIC;
                case 9 -> brain.state = PetState.DANCE;
                case 10 -> { full.blend = 1; full.mix = 0.6f; full.previousMix = 0.4f; }
                default -> { }
            }
            cues.update(brain, motion, puppet, full);
            if (frame == 4) assertTrue(cues.beerOpened);
        }
        assertEquals(2, backend.count(PetSfxAction.STEP));
        for (PetSfxAction action : new PetSfxAction[]{PetSfxAction.JUMP, PetSfxAction.FALL, PetSfxAction.LAND,
            PetSfxAction.SIT, PetSfxAction.LIE_DOWN, PetSfxAction.WAKE, PetSfxAction.SCARED})
            assertEquals(1, backend.count(action), action.key);
    }

    @Test
    void wordsAndActualBottlePhysicsEmitMatchingSounds() {
        Backend backend = new Backend();
        PetSfx sfx = new PetSfx(backend);
        PetEffects effects = new PetEffects(sfx);
        PetSfxAction[] words = {PetSfxAction.BURP, PetSfxAction.HIC, PetSfxAction.BEER_OPEN,
            PetSfxAction.COUGH, PetSfxAction.SNORE, PetSfxAction.BOTTLE_SHATTER};
        for (int i = 0; i < words.length; i++) {
            backend.finish();
            sfx.update(i * 2000L, true, false, 1);
            effects.word(i, 50, 50);
            assertEquals(1, backend.count(words[i]));
        }
        backend.finish();
        sfx.update(20000, true, false, 1);
        effects.throwBottle(50, 50, 1, true, 100, 100, 40);
        PetResources.Rig rig = new PetResources.Rig(100, 100, new PetResources.Part[0], new float[0],
            null, 10, 20, 5, 0, new PetResources.FullPose[0], new PetResources.FullPose[0], new int[0], new int[0]);
        PetBrain brain = new PetBrain(1002);
        brain.state = PetState.REST;
        PetMotion motion = new PetMotion(77);
        motion.width = motion.height = 100;
        for (int i = 1; i <= 200; i++) {
            backend.finish();
            sfx.update(20000 + i * 50L, true, false, 1);
            effects.update(0.05f, brain, motion, rig, 40, 50, 50, 50, 50, true);
        }
        assertEquals(1, backend.count(PetSfxAction.THROW));
        assertTrue(backend.count(PetSfxAction.BOTTLE_BOUNCE) >= 2);
        assertEquals(2, backend.count(PetSfxAction.BOTTLE_SHATTER));
    }

    @Test
    void schedulingAndCuesAllocateNothingAfterWarmup() {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        Backend backend = new Backend();
        PetSfx sfx = new PetSfx(backend);
        PetSfxCues cues = new PetSfxCues(sfx);
        PetBrain brain = new PetBrain(1004);
        PetMotion motion = new PetMotion(77);
        PetPose puppet = new PetPose();
        PetFullPose full = new PetFullPose();
        long time = 0;
        for (int i = 0; i < 100000; i++) {
            backend.finish();
            sfx.update(time++, true, false, 1);
            cues.update(brain, motion, puppet, full);
            sfx.play(PetSfxAction.STEP);
        }
        long least = Long.MAX_VALUE;
        for (int sample = 0; sample < 5; sample++) {
            long before = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
            for (int i = 0; i < 10000; i++) {
                backend.finish();
                sfx.update(time++, true, false, 1);
                cues.update(brain, motion, puppet, full);
                sfx.play(PetSfxAction.STEP);
            }
            least = Math.min(least, bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before);
        }
        assertEquals(0, least);
    }
}
